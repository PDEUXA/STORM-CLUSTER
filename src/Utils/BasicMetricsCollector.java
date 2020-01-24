package Utils;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.apache.storm.generated.Nimbus;
import org.apache.storm.utils.Utils;


public class BasicMetricsCollector implements AutoCloseable {
    /* headers */
    public static final String TIME = "elapsed (sec)";
    public static final String TIME_FORMAT = "%d";
    public static final String WORKERS = "workers";
    public static final String TASKS = "tasks";
    public static final String EXECUTORS = "executors";

    private static final Logger LOG = Logger.getLogger(BasicMetricsCollector.class);
    final MetricsCollectorConfig config;
    //    final StormTopology topology;
    final Set<String> header = new LinkedHashSet<String>();
    final Map<String, String> metrics = new HashMap<String, String>();
    final boolean collectTopologyStats;

    int lineNumber = 0;
    boolean first = true;
    private PrintWriter dataWriter;
    private long startTime = 0;
    private MetricsSample lastSample;
    private MetricsSample curSample;
    private double maxLatency = 0;

    public BasicMetricsCollector(String topoName, Map<String, Object> topoConfig) {
        Set<MetricsItem> items = getMetricsToCollect();
        this.config = new MetricsCollectorConfig(topoName, topoConfig);
        collectTopologyStats = collectTopologyStats(items);
        dataWriter = new PrintWriter(System.err);
    }

    private Set<MetricsItem> getMetricsToCollect() {
        Set<MetricsItem> result = new HashSet<>();
        result.add(MetricsItem.ALL);
        return result;
    }

    public void collect(Nimbus.Iface client) {
        try {
            if (!first) {
                this.lastSample = this.curSample;
                this.curSample = MetricsSample.factory(client, config.name);
                updateStats(dataWriter);
                writeLine(dataWriter);
            } else {
                LOG.info("Getting baseline metrics sample.");
                writeHeader(dataWriter);
                this.curSample = MetricsSample.factory(client, config.name);
                first = false;
                startTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            LOG.error("storm metrics failed! ", e);
        }
    }

    @Override
    public void close() {
        dataWriter.close();
    }

    boolean updateStats(PrintWriter writer)
            throws Exception {
        if (collectTopologyStats) {
            updateTopologyStats();
        }
        return true;
    }

    void updateTopologyStats() {
        long timeTotal = System.currentTimeMillis() - startTime;
        int numWorkers = this.curSample.getNumWorkers();
        int numExecutors = this.curSample.getNumExecutors();
        int numTasks = this.curSample.getNumTasks();

        metrics.put(TIME, String.format(TIME_FORMAT, timeTotal / 1000));
        metrics.put(WORKERS, Integer.toString(numWorkers));
        metrics.put(EXECUTORS, Integer.toString(numExecutors));
        metrics.put(TASKS, Integer.toString(numTasks));
    }


    void writeHeader(PrintWriter writer) {
        header.add(TIME);
        if (collectTopologyStats) {
            header.add(WORKERS);
            header.add(TASKS);
            header.add(EXECUTORS);
        }


        writer.println(
                "\n------------------------------------------------------------------------------------------------------------------");
        String str = Utils.join(header, ",");
        writer.println(str);
        writer
                .println("------------------------------------------------------------------------------------------------------------------");
        writer.flush();
    }

    void writeLine(PrintWriter writer) {
        List<String> line = new LinkedList<String>();
        for (String h : header) {
            line.add(metrics.get(h));
        }
        String str = Utils.join(line, ",");
        writer.println(str);
        writer.flush();
    }

    boolean collectTopologyStats(Set<MetricsItem> items) {
        return items.contains(MetricsItem.ALL)
                || items.contains(MetricsItem.TOPOLOGY_STATS);
    }

    public enum MetricsItem {
        TOPOLOGY_STATS,
        ALL
    }

    public static class MetricsCollectorConfig {
        private static final Logger LOG = Logger.getLogger(MetricsCollectorConfig.class);

        // storm configuration
        public final Map<String, Object> topoConfig;
        // storm topology name
        public final String name;
        // benchmark label
        public final String label;

        public MetricsCollectorConfig(String topoName, Map<String, Object> topoConfig) {
            this.topoConfig = topoConfig;
            String labelStr = (String) topoConfig.get("benchmark.label");
            this.name = topoName;
            if (labelStr == null) {
                LOG.warn("'benchmark.label' not found in config. Defaulting to topology name");
                labelStr = this.name;
            }
            this.label = labelStr;
        }
    } // MetricsCollectorConfig

}
