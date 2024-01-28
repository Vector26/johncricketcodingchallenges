package org.johncricketCodingChallenges.loadBalancer.loadbalancer;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHasher {
    private final SortedMap<BigInteger, String> circle = new TreeMap<>();

    public void addServer(String serverAddress) {
        BigInteger hash = getHash(serverAddress);
        circle.put(hash, serverAddress);
    }

    public void removeServer(String serverAddress) {
        BigInteger hash = getHash(serverAddress);
        circle.remove(hash);
    }

    public String getServer(String request) {
        if (circle.isEmpty()) {
            return null;
        }
        BigInteger hash = getHash(request);
        if (!circle.containsKey(hash)) {
            SortedMap<BigInteger, String> tailMap = circle.tailMap(hash);
            // Basically if the sub-map of all the keys that are less than the request host's hash key is empty, it picks ip the first from the original sortedMap
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }

    private BigInteger getHash(String key) {
        String salt = "qwertyuuiip";
        try {
            key=salt+key;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(key.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            return no;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

