package Topology;

import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;
import java.util.Map;
import Utils.helper;

public class WordCountTopo {
    public static final String SPOUT_ID = "spout";
    public static final String COUNT_ID = "counter";
    public static final String REPORT_ID = "reporter";
    public static final String SPLIT_ID = "splitter";
    public static final String TOPOLOGY_NAME = "WordCountTopo";

    // Config settings
    public static final String METRIC_COUNT = "metric.count";
    public static int metricCount = 1;

    public static final String SPOUT_NUM = "spout.count";
    public static final String SPLIT_NUM = "splitter.count";
    public static final String COUNT_NUM = "counter.count";
    public static final String REPO_NUM = "reporter.count";
    public static final String SPOUT_TASK = "spout.task";
    public static final String SPLIT_TASK = "splitter.task";
    public static final String COUNT_TASK = "counter.task";

    public static final int DEFAULT_SPOUT_NUM = 4;
    public static final int DEFAULT_SPLIT_BOLT_NUM = 2;
    public static final int DEFAULT_COUNT_BOLT_NUM = 2;
    public static final int DEFAULT_REPO_BOLT_NUM = 1;

    public static final int DEFAULT_SPOUT_TASK = 1;
    public static final int DEFAULT_SPLIT_TASK= 1;
    public static final int DEFAULT_COUNT_TASK = 1;
    public static final int DEFAULT_METRIC_COUNT = 1;


    static StormTopology getTopology(Map<String, Object> config) {

        final int spoutNum = helper.getInt(config, SPOUT_NUM, DEFAULT_SPOUT_NUM);
        final int spBoltNum = helper.getInt(config, SPLIT_NUM, DEFAULT_SPLIT_BOLT_NUM);
        final int cntBoltNum = helper.getInt(config, COUNT_NUM, DEFAULT_COUNT_BOLT_NUM);
        final int rptBoltNum = helper.getInt(config, REPO_NUM, DEFAULT_REPO_BOLT_NUM);
        final int spoutTask = helper.getInt(config, SPOUT_TASK, DEFAULT_SPOUT_TASK);
        final int splitTask = helper.getInt(config, SPLIT_TASK, DEFAULT_SPLIT_TASK);
        final int countTask = helper.getInt(config, COUNT_TASK, DEFAULT_COUNT_TASK);
        metricCount = helper.getInt(config, METRIC_COUNT, DEFAULT_METRIC_COUNT);


        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(SPOUT_ID, new RandomSentenceSpout(), spoutNum).setNumTasks(spoutTask);
        builder.setBolt(SPLIT_ID, new SplitBolt(), spBoltNum).setNumTasks(splitTask).localOrShuffleGrouping(SPOUT_ID);
        builder.setBolt(COUNT_ID, new CountBolt(), cntBoltNum).setNumTasks(countTask).fieldsGrouping(SPLIT_ID, new Fields(SplitBolt.FIELDS));
        builder.setBolt(REPORT_ID, new ReportBolt(), rptBoltNum).globalGrouping(COUNT_ID);
        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception {
        int runTime = -1;
        Config topoConf = new Config();
        if (args.length > 0) {
            runTime = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            runTime = Integer.parseInt(args[0]);
            topoConf.putAll(Utils.findAndReadConfigFile(args[1]));
        }
        topoConf.put(Config.TOPOLOGY_PRODUCER_BATCH_SIZE, 1000);
        topoConf.put(Config.TOPOLOGY_BOLT_WAIT_STRATEGY, "org.apache.storm.policy.WaitStrategyPark");
        topoConf.put(Config.TOPOLOGY_BOLT_WAIT_PARK_MICROSEC, 0);
        topoConf.put(Config.TOPOLOGY_DISABLE_LOADAWARE_MESSAGING, true);
        topoConf.put(Config.TOPOLOGY_STATS_SAMPLE_RATE, 0.0005);
        topoConf.put(Config.TOPOLOGY_MAX_SPOUT_PENDING, 1);
        topoConf.putAll(Utils.readCommandLineOpts());
        topoConf.registerMetricsConsumer(org.apache.storm.metric.LoggingMetricsConsumer.class, 1);

        if (args.length > 2) {
            System.err.println("args: [runDurationSec]  [optionalConfFile]");
            return;
        }
        //  Submit topology to storm cluster
        helper.runOnClusterAndPrintMetrics(runTime, TOPOLOGY_NAME, topoConf, getTopology(topoConf));
    }
}
