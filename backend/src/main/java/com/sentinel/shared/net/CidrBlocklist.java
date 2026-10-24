package com.sentinel.shared.net;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance bitmask CIDR evaluator implementing RFC-compliant IP subnets and metadata blocking.
 */
@Component
public class CidrBlocklist {

    private final List<CidrRange> loopbackRanges = new ArrayList<>();
    private final List<CidrRange> metadataRanges = new ArrayList<>();
    private final List<CidrRange> privateAndRestrictedRanges = new ArrayList<>();

    public CidrBlocklist() {
        // Loopback ranges
        addRange(loopbackRanges, "127.0.0.0/8");
        addRange(loopbackRanges, "0.0.0.0/8");
        addRange(loopbackRanges, "::1/128");

        // Cloud Metadata & Link-Local (AWS, GCP, Azure, RFC 3927)
        addRange(metadataRanges, "169.254.0.0/16");
        addRange(metadataRanges, "fe80::/10");

        // RFC 1918 Private IPv4
        addRange(privateAndRestrictedRanges, "10.0.0.0/8");
        addRange(privateAndRestrictedRanges, "172.16.0.0/12");
        addRange(privateAndRestrictedRanges, "192.168.0.0/16");

        // Unique Local IPv6 (RFC 4193)
        addRange(privateAndRestrictedRanges, "fc00::/7");

        // Multicast & Broadcast
        addRange(privateAndRestrictedRanges, "224.0.0.0/4");
        addRange(privateAndRestrictedRanges, "255.255.255.255/32");
        addRange(privateAndRestrictedRanges, "ff00::/8");

        // Carrier-Grade NAT (RFC 6598)
        addRange(privateAndRestrictedRanges, "100.64.0.0/10");

        // Documentation & benchmark ranges (RFC 5737 / RFC 2544)
        addRange(privateAndRestrictedRanges, "192.0.2.0/24");
        addRange(privateAndRestrictedRanges, "198.51.100.0/24");
        addRange(privateAndRestrictedRanges, "203.0.113.0/24");
        addRange(privateAndRestrictedRanges, "198.18.0.0/15");
    }

    private void addRange(List<CidrRange> list, String cidr) {
        try {
            list.add(CidrRange.parse(cidr));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to parse static CIDR range: " + cidr, e);
        }
    }

    /**
     * Checks if an IP address falls into a blocked CIDR range.
     *
     * @param address            the IP address to evaluate
     * @param allowLocalTargets  if true, loopback addresses (127.0.0.0/8, ::1) are allowed (for local testing)
     * @return true if the address is blocked
     */
    public boolean isBlocked(InetAddress address, boolean allowLocalTargets) {
        if (address == null) {
            return true;
        }

        // Cloud metadata is strictly forbidden under all circumstances
        for (CidrRange range : metadataRanges) {
            if (range.contains(address)) {
                return true;
            }
        }

        // Loopback addresses are blocked unless local targets are explicitly allowed
        if (!allowLocalTargets) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                return true;
            }
            for (CidrRange range : loopbackRanges) {
                if (range.contains(address)) {
                    return true;
                }
            }
        }

        // RFC 1918, Link-local, multicast, and restricted ranges
        if (!allowLocalTargets) {
            if (address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                return true;
            }
            for (CidrRange range : privateAndRestrictedRanges) {
                if (range.contains(address)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class CidrRange {
        private final boolean isIpv4;
        private final BigInteger network;
        private final BigInteger mask;

        public CidrRange(boolean isIpv4, BigInteger network, BigInteger mask) {
            this.isIpv4 = isIpv4;
            this.network = network;
            this.mask = mask;
        }

        public static CidrRange parse(String cidr) throws UnknownHostException {
            int slashIndex = cidr.indexOf('/');
            String ipPart;
            int prefixLength;

            if (slashIndex > 0) {
                ipPart = cidr.substring(0, slashIndex);
                prefixLength = Integer.parseInt(cidr.substring(slashIndex + 1));
            } else {
                ipPart = cidr;
                prefixLength = cidr.contains(":") ? 128 : 32;
            }

            InetAddress inetAddress = InetAddress.getByName(ipPart);
            byte[] bytes = inetAddress.getAddress();
            boolean isIpv4 = bytes.length == 4;
            int totalBits = isIpv4 ? 32 : 128;

            if (prefixLength < 0 || prefixLength > totalBits) {
                throw new IllegalArgumentException("Invalid prefix length: " + prefixLength);
            }

            BigInteger ipNum = new BigInteger(1, bytes);
            BigInteger maskNum;
            if (prefixLength == 0) {
                maskNum = BigInteger.ZERO;
            } else {
                BigInteger allOnes = BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE);
                maskNum = allOnes.shiftLeft(totalBits - prefixLength).and(allOnes);
            }

            BigInteger networkNum = ipNum.and(maskNum);
            return new CidrRange(isIpv4, networkNum, maskNum);
        }

        public boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            boolean addressIsIpv4 = bytes.length == 4;
            if (this.isIpv4 != addressIsIpv4) {
                return false;
            }

            BigInteger ipNum = new BigInteger(1, bytes);
            return ipNum.and(mask).equals(network);
        }
    }
}
