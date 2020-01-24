package Topology;

import org.apache.storm.metric.api.CountMetric;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;

public class CountBolt extends BaseBasicBolt {
    public static final String FIELDS_WORD = "word";
    public static final String FIELDS_COUNT = "count";
    private transient CountMetric countMetric;

    Map<String, Integer> counts = new HashMap<>();

    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context) {
        countMetric =  new CountMetric();
        context.registerMetric("execute_count", countMetric, 1);

    }

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
        String word = tuple.getString(0);
        Integer count = counts.get(word);
        if (count == null) {
            count = 0;
        }
        count++;
        countMetric.incr();
        counts.put(word, count);
        collector.emit(new Values(word, count));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELDS_WORD, FIELDS_COUNT));
    }
}