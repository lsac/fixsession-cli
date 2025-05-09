package fixsession.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.MessageUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public enum Mutator {
    INST;
    private static final Logger LOG = LogManager.getLogger();

    private final HashMap<String, HashSet<Tag>> tags;

    Mutator() {
        tags = new HashMap<>();
    }

    public void process(Message message) {
        if (tags.size() < 1) {
            LOG.debug("mutate map is empty");
            return;
        }
        try {
            String szType = MessageUtils.getMessageType(message.toString());
            if (tags.containsKey(szType)) {
                tags.get(szType).forEach(tag -> {
                    tag.mutate(message);
                    LOG.debug("{} is mutated", szType);
                });
            }
        } catch (InvalidMessage e) {
            LOG.error("unable to mutate {}", message, e);
        }
    }

    @Override
    public String toString() {
        return "Mutator{" +
                "tags=" + tags +
                '}';
    }

    public String config(String cli) {
        String ret = null;
        if (StringUtils.isEmpty(cli)) {
            LOG.debug("mutate cli is empty");
            return ret;
        }
        LOG.debug("mutate cli is [{}]", cli);

        try {
            if (cli.equalsIgnoreCase("show")) {
                LOG.debug("tag map is {}, size is {}", tags, tags.size());
                return "tag map is " + tags.toString();
            } else if (cli.equalsIgnoreCase("clear")) {
                tags.clear();
                LOG.debug("tag map emptied, size is {}", tags.size());
                return "tag map cleared";
            } else if (cli.startsWith("clear ")) {
                String[] split = cli.split(" ");
                if (split.length >= 2) {
                    if(!tags.containsKey((split[1])))
                        return "tag map does not have message type " + split[1];
                    tags.remove(split[1]);
                    LOG.debug("map removed message type {}", split[1]);
                    return "tag map cleared message type " + split[1];
                } else
                    return "invalid arguments";
            } else {
                String[] split = cli.split(" ");
                ACTION action = ACTION.valueOf(split[1]);
                if ((action==ACTION.SET || action== ACTION.ADD || action==ACTION.HSET || action== ACTION.HADD ) && split.length != 4) {
                    LOG.debug("failed to get 4 arguments [{}]", split);
                    return "invalid cli";
                }
                else if ((action==ACTION.EMPTY || action== ACTION.REMOVE || action==ACTION.HEMPTY || action== ACTION.HREMOVE ) && split.length != 3) {
                    LOG.debug("failed to get 3 arguments [{}]", split);
                    return "invalid cli";
                }
                else {
                    config(split[0], action, Integer.parseInt(split[2]), split.length == 3 ? null:split[3]);
                }
            }
            ret = "processing " + cli;

        } catch (Exception e) {
            LOG.error("failure in cli {}", cli, e);
        }
        return ret;
    }

    public void config(String type, ACTION action, int tag, String value) {
        HashSet<Tag> tags1 = tags.computeIfAbsent(type, t -> new HashSet<>());
        Tag tag1 = new Tag(action, tag, value);
        tags1.add(tag1);
        LOG.debug("tag {} is now {}", tag, tag1);
    }

    public void clear() {
        tags.clear();
    }

    public int size() {
        return tags.size();
    }

    public Set<Tag> getMsgTypeMutation(String type) {
        return tags.getOrDefault(type, new HashSet<>());
    }

    enum ACTION {ADD, HADD, SET, HSET, REMOVE, HREMOVE, EMPTY, HEMPTY}

    class Tag {
        ACTION action;
        int tag;
        String value;

        public Tag(ACTION action, int tag, String value) {
            this.action = action;
            this.tag = tag;
            this.value = value;
        }

        public void mutate(Message message) {
            if (message == null) {
                return;
            }
            switch (action) {
                case ADD, SET -> {
                    message.setString(tag, value);
                }
                case EMPTY -> {
                    message.setString(tag, "");
                }
                case REMOVE -> {
                    message.removeField(tag);
                }
                case HADD, HSET -> {
                    message.getHeader().setString(tag, value);
                }
                case HEMPTY -> {
                    message.getHeader().setString(tag, "");
                }
                case HREMOVE -> {
                    message.getHeader().removeField(tag);
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tag tag1 = (Tag) o;
            return tag == tag1.tag;
        }

        @Override
        public int hashCode() {
            return tag;
        }

        @Override
        public String toString() {
            return "Tag{" +
                    "action=" + action +
                    ", tag=" + tag +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}
