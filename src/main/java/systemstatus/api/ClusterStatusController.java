package systemstatus.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import systemstatus.gto.*;
import systemstatus.service.*;

@RestController
public class ClusterStatusController {

    private final ClusterStatusService clusterService;


    public ClusterStatusController(ClusterStatusService clusterService){
        this.clusterService = clusterService;
    }    

    @GetMapping("/cluster/status")
    public ClusterStatusGto clusteStatus() throws Exception{
        return clusterService.clusterStatus();
    }
}
