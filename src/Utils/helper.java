package Utils;


import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.KillOptions;
import org.apache.storm.generated.Nimbus;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.NimbusClient;
import org.apache.storm.utils.ObjectReader;
import org.apache.storm.utils.Utils;

import java.util.Map;

public class helper {

    public static void kill(Nimbus.Iface client, String topoName) throws Exception {
        KillOptions opts = new KillOptions();
        opts.set_wait_secs(0);
        client.killTopologyWithOpts(topoName, opts);
    }

    public static int getInt(Map map, Object key, int def) {
        return ObjectReader.getInt(Utils.get(map, key, def));
    }

    public static String getStr(Map map, Object key) {
        return (String) map.get(key);
    }

    public static void collectMetricsAndKill(String topologyName, Integer pollInterval, int duration) throws Exception {
        Map<String, Object> clusterConf = Utils.readStormConfig();
        Nimbus.Iface client = NimbusClient.getConfiguredClient(clusterConf).getClient();
        try (BasicMetricsCollector metricsCollector = new BasicMetricsCollector(topologyName, clusterConf)) {

            if (duration > 0) {
                int times = duration / pollInterval;
                metricsCollector.collect(client);
                for (int i = 0; i < times; i++) {
                    Thread.sleep(pollInterval * 1000);
                    metricsCollector.collect(client);
                }
            } else {
                while (true) { //until Ctrl-C
                    metricsCollector.collect(client);
                    Thread.sleep(pollInterval * 1000);
                }
            }
        } finally {
            kill(client, topologyName);
        }
    }

    /**
     * Kill topo on Ctrl-C.
     */
    public static void setupShutdownHook(final String topoName) {
        Map<String, Object> clusterConf = Utils.readStormConfig();
        final Nimbus.Iface client = NimbusClient.getConfiguredClient(clusterConf).getClient();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Killing...");
                    helper.kill(client, topoName);
                    System.out.println("Killed Topology");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void runOnClusterAndPrintMetrics(int durationSec, String topoName, Map<String, Object> topoConf, StormTopology topology)
            throws Exception {
        // submit topology
        StormSubmitter.submitTopologyWithProgressBar(topoName, topoConf, topology);
        setupShutdownHook(topoName); // handle Ctrl-C

        // poll metrics every minute, then kill topology after specified duration
        Integer pollIntervalSec = 60;
        collectMetricsAndKill(topoName, pollIntervalSec, durationSec);
    }
}