package systemstatus.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import systemstatus.gto.CpuStatusGto;
import systemstatus.gto.DiskStatusGto;
import systemstatus.gto.DockerStatusGto;
import systemstatus.gto.KernelStatusGto;
import systemstatus.gto.MemoryStatusGto;
import systemstatus.gto.NvmeStatusGto;
import systemstatus.util.CommandRunner;

@Service
public class SystemStatusService {

    private static final Path TEMP_PATH = Path.of("/sys/class/thermal/thermal_zone0/temp");
    private static final Path MEM_PATH = Path.of("/proc/meminfo");

    private static final Path OS_TYPE_PATH = Path.of("/proc/sys/kernel/ostype");
    private static final Path OS_VERSION_PATH = Path.of("/proc/sys/kernel/osrelease");
    private static final Path ARCH_PATH = Path.of("/proc/sys/kernel/arch");
    private static final Path HOSTNAME_PATH = Path.of("/proc/sys/kernel/hostname");

    private static final Pattern NVME_TEMP_PATTERN = Pattern.compile("temperature\\s*:\\s*([\\d.]+)");
    private static final Pattern NVME_PERCENTAGE_PATTERN = Pattern.compile("percentage_used\\s*:\\s*([\\d.]+)%?");
    private static final Pattern NVME_WARNING_PATTERN = Pattern.compile("critical_warning\\s*:\\s*(\\d+)");

    private static final int USR_INDEX = 2;
    private static final int SYS_INDEX = 4;
    private static final int IDLE_INDEX = 11;

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);


    public double getTemp() throws IOException, FileNotFoundException{
        String temp = Files.readString(TEMP_PATH).trim();
        double milliTemp = Double.parseDouble(temp);
        return milliTemp/1000;
    }

    public boolean isThrottled() throws IOException, InterruptedException{
        var res = CommandRunner.run(List.of("vcgencmd", "get_throttled"), Duration.ofSeconds(5));
        if (res.timedOut()) throw new RuntimeException("vcgencmd timed out");
        if (res.exitCode() != 0) throw new RuntimeException("vcgencmd failed: " + res.stderr());

        boolean isThrottled = false;
        String hexString = res.stdout();
        if (hexString.startsWith("throttled=0x")) {
            hexString = hexString.substring(("throttled=0x").length()).strip();
        }
        try{
            int decimalValue = Integer.parseInt(hexString);
            isThrottled = (decimalValue != 0);
        } catch(NumberFormatException e){
            log.error("isThrottled() failed", e);
        }
        return isThrottled;
    }


    public NvmeStatusGto getNvme()throws IOException, InterruptedException{
        try {
            var res = CommandRunner.run(List.of("sudo", "-n", "/usr/sbin/nvme", "smart-log", "/dev/nvme0n1"), Duration.ofSeconds(5));
            if (res.timedOut()) throw new RuntimeException("nvme timed out");
            if (res.exitCode() != 0) {
                throw new RuntimeException("nvme failed: " + res.stderr());
            }
            double nvmeTempC = 0;
            double percentageWear = 0;
            int criticalWarning = 0;
            for (String line : res.stdout().lines().filter(l -> l.contains("temperature") || l.contains("percentage_used") || l.contains("critical_warning")).toList()){
                Matcher percentMatcher = NVME_PERCENTAGE_PATTERN.matcher(line);
                if (percentMatcher.find()){
                    try{
                        percentageWear = Double.parseDouble(percentMatcher.group(1));
                    } catch(NumberFormatException e){
                        //Log error
                    }
                }
                Matcher warningMatcher = NVME_WARNING_PATTERN.matcher(line);
                if (warningMatcher.find()){
                    try{
                        criticalWarning = Integer.parseInt(warningMatcher.group(1));                
                    } catch(NumberFormatException e){
                        //Log error
                    }
                }
                Matcher tempMatcher = NVME_TEMP_PATTERN.matcher(line);
                if (tempMatcher.find()){
                    try{
                        nvmeTempC = Double.parseDouble(tempMatcher.group(1));                
                    } catch(NumberFormatException e){
                        log.error("getNvme() failed", e);
                    }
                }
            }
            return new NvmeStatusGto(nvmeTempC, percentageWear, criticalWarning);

        }catch (Exception e){
            log.warn("getNvme() ikke tilgjengelig: {}", e.getMessage());
            return new NvmeStatusGto(0,0,0);
        }
    }

    public CpuStatusGto getCpu() throws IOException, InterruptedException{
        var res = CommandRunner.run(List.of("mpstat", "1", "1"), Duration.ofSeconds(5));
        if (res.timedOut()) throw new RuntimeException("mpstat timed out");
        if (res.exitCode() != 0) {
            throw new RuntimeException("mpstat failed. " + res.stderr());
        }
        double usr = 0, sys = 0, idle = 0;
        for(String line : res.stdout().lines().filter(l -> l.contains("all")).toList()){
            String[] parts = line.trim().split("\\s+");
            if (parts.length > IDLE_INDEX){
                try{
                    usr = Double.parseDouble(parts[USR_INDEX]);
                    sys = Double.parseDouble(parts[SYS_INDEX]);
                    idle = Double.parseDouble(parts[IDLE_INDEX]);
                } catch(NumberFormatException e){
                    log.error("getCpu() failed", e);
                }
            }
        }
        double cpuTempC = getTemp();
        boolean throttled = isThrottled();
        return new CpuStatusGto(cpuTempC, usr, sys, idle, throttled);
    }

    public MemoryStatusGto getMemory() throws IOException, FileNotFoundException{
        List<String> memoryLines = Files.readAllLines(MEM_PATH);
        long memTotalMb = 0, memAvailableMb = 0, memUsedMb = 0, swapTotalMb = 0, swapFreeMb = 0;
        for(String line : memoryLines){
            String[] parts = line.trim().split("\\s+");

            if (line.startsWith("MemTotal:")){
                memTotalMb = Long.parseLong(parts[1])/1024;
            } else if ( line.startsWith("MemAvailable:")){
                memAvailableMb = Long.parseLong(parts[1])/1024;
            } else if ( line.startsWith("SwapTotal:")){
                swapTotalMb = Long.parseLong(parts[1])/1024;
            } else if ( line.startsWith("SwapFree:")){
                swapFreeMb = Long.parseLong(parts[1])/1024;
            }
        }

        memUsedMb = memTotalMb - memAvailableMb;
        return new MemoryStatusGto(memTotalMb, memUsedMb, swapTotalMb, swapFreeMb);
    }

    public List<DiskStatusGto> getDisks() throws IOException{
        List<DiskStatusGto> disks = new ArrayList<>();

        for(String path : getMountPoints()){
            try{
                FileStore store = Files.getFileStore(Path.of(path));

                float total = store.getTotalSpace();
                float usable = store.getUsableSpace();
                float used = total - usable;

                disks.add(new DiskStatusGto(path, total / (1024*1024*1024),used / (1024*1024*1024),(int)((used * 100.0) / total)
            ));
            } catch(IOException e){
                log.error("getDisks() failed", e);

            }
        }
        return disks;
    }

    public KernelStatusGto getKernel() throws IOException {
        String osType = Files.readString(OS_TYPE_PATH).trim();
        String version = Files.readString(OS_VERSION_PATH).trim();
        String arch = Files.readString(ARCH_PATH).trim();
        String hostName = Files.readString(HOSTNAME_PATH). trim();
    
        return new KernelStatusGto(osType + " " + version, arch, hostName);
    }

    public List<DockerStatusGto> getDockerContainers() throws IOException, InterruptedException{
        List<DockerStatusGto> containers = new ArrayList<>();
        var res = CommandRunner.run(List.of("sudo", "-n", "docker", "ps", "--format", "{{.Names}}|{{.ID}}|{{.Image}}|{{.Status}}|{{.RunningFor}}"), Duration.ofSeconds(5));
        if (res.timedOut()) throw new RuntimeException("nvme timed out");
        if (res.exitCode() != 0) {
            throw new RuntimeException("getDockerStatus() failed: " + res.stderr());
        }
        for(String line : res.stdout().lines().filter(l -> !l.isBlank()).toList()){
            String[] parts = line.split("\\|");
            if (parts.length == 5){
                containers.add(new DockerStatusGto(
                    parts[0], //NAME
                    parts[1], //ID
                    parts[2], //IMAGE
                    parts[3], //STATUS
                    parts[4]  //RUNNING_FOR
                ));
            }
        }
        return containers;
    }

    private List<String> getMountPoints() throws IOException {
        return Files.readAllLines(Path.of("/proc/mounts")).stream()
        .map(line -> line.split("\\s+"))
        .filter(parts -> parts.length >= 3)
        .filter(parts -> parts[2].matches("ext4|xfs|btrfs|vfat|ntfs"))
        .map(parts -> parts[1])
        .toList();
    }
}
