package com.typeahead.hashing;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ConsistentHashRing {
    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);

    private final TreeMap<Integer, String> ring = new TreeMap<>();
    private final int virtualNodes = 150;

    @PostConstruct
    public void init() {
        addNode("node-a");
        addNode("node-b");
        addNode("node-c");
        log.info("Initialized ConsistentHashRing with nodes node-a, node-b, node-c ({} total virtual nodes)", ring.size());
    }

    public synchronized void addNode(String nodeName) {
        for (int i = 0; i < virtualNodes; i++) {
            int hash = hash(nodeName + "-" + i);
            ring.put(hash, nodeName);
        }
    }

    public synchronized void removeNode(String nodeName) {
        ring.entrySet().removeIf(entry -> entry.getValue().equals(nodeName));
    }

    public synchronized String getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        int hash = hash(key);
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    public int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24) |
                   ((digest[1] & 0xFF) << 16) |
                   ((digest[2] & 0xFF) << 8)  |
                   (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public synchronized Map<String, Integer> getNodeDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        for (String node : ring.values()) {
            distribution.merge(node, 1, Integer::sum);
        }
        return distribution;
    }
}
