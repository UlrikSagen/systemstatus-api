package systemstatus.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import systemstatus.gto.*;

@Service
public class ClusterStatusService {

    private final SystemStatusService service;

    private static final Logger log = LoggerFactory.getLogger(ClusterStatusService.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    @Value("${cluster.nodes}")
    private List<String> nodeList;

    public ClusterStatusService(SystemStatusService service){
        this.service = service;
    }
    public ClusterStatusGto clusterStatus(){
        List<NodeStatusGto> nodes = new ArrayList<>();

        try{
            SystemStatusGto local = service.getStatus();
            nodes.add(new NodeStatusGto("http://192.168.50.5", local, true));
        }catch(Exception e){
            nodes.add(new NodeStatusGto("http://192.168.50.5", null, false));
            log.error(e.getMessage());
        }
        for(String node : nodeList){
            try{
                nodes.add(nodeStatus(node));
            }catch(Exception e){
                nodes.add(new NodeStatusGto(node, null, false));
                log.error(e.getMessage());
            }
        }
        return new ClusterStatusGto(nodes);
    }
    
    private NodeStatusGto nodeStatus(String node) throws Exception{
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(node + "/status"))
                .header("Accept", "application/json")
                .GET()
                .build(); 
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("feilet: " + res.statusCode() + res.body() + "\n");
        }
        SystemStatusGto systemStatus = objectMapper.readValue(res.body(), SystemStatusGto.class);
        return new NodeStatusGto(node, systemStatus, true);
    }
}
