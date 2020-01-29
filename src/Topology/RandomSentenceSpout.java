package Topology;


import org.apache.storm.metric.api.CountMetric;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class RandomSentenceSpout extends BaseRichSpout {
    private static final Logger LOG = LoggerFactory.getLogger(RandomSentenceSpout.class);

    SpoutOutputCollector collector;
    Random rand;
    private transient CountMetric spoutMetric;

    @Override
    public void open(Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
        rand = new Random();
        spoutMetric = new CountMetric();
        context.registerMetric("spout_count", spoutMetric, 1);
    }

    @Override
    public void nextTuple() {
        Utils.sleep(100);
        String[] sentences = new String[]{
                sentence("the cow jumped over the moon"), sentence("an apple a day keeps the"),
                sentence("four score and seven years ago"), sentence("snow white and the seven dwarfs"), sentence("i am at two with nature")
        };
        final String sentence = sentences[rand.nextInt(sentences.length)];

        LOG.debug("Emitting tuple: {}", sentence);
        spoutMetric.incr();
        collector.emit(new Values(sentence));

    }

    protected String sentence(String input) {
        return input;
    }


    @Override
    public void fail(Object id) {
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

        declarer.declare(new Fields("word"));
    }

    // Add unique identifier to each tuple, which is helpful for debugging
    public static class TimeStamped extends RandomSentenceSpout {
        private final String prefix;

        public TimeStamped() {
            this("");
        }

        public TimeStamped(String prefix) {
            this.prefix = prefix;
        }

        @Override
        protected String sentence(String input) {
            return prefix + currentDate() + " " + input;
        }

        private String currentDate() {
            return new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss.SSSSSSSSS").format(new Date());
        }
    }

    @Override
    public void ack(Object msgId) {
        super.ack(msgId);
    }
}