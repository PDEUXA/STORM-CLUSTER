package Topology;

import org.apache.storm.metric.api.CountMetric;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class SplitBolt extends BaseBasicBolt {
    public static final String FIELDS = "word";
    private transient CountMetric splitMetric;

    public static String[] splitSentence(String sentence) {
        if (sentence != null) {
            return sentence.split("\\s+");
        }
        return null;
    }

    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context) {
        splitMetric = new CountMetric();
        context.registerMetric("execute_split", splitMetric, 1);

    }

    @Override
    public void execute(Tuple input, BasicOutputCollector collector) {
        splitMetric.incr();
        for (String word : splitSentence(input.getString(0))) {
            collector.emit(new Values(word));
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELDS));
    }
}
