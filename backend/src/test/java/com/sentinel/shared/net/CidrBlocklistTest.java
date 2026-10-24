package com.sentinel.shared.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class CidrBlocklistTest {

    private CidrBlocklist cidrBlocklist;

    @BeforeEach
    void setUp() {
        cidrBlocklist = new CidrBlocklist();
    }

    @Test
    @DisplayName("SSRF-01: Loopback IPv4 and IPv6 addresses are blocked when allowLocalTargets is false")
    void shouldBlockLoopbackAddresses() throws Exception {
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("127.0.0.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("127.255.255.254"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("::1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("0.0.0.0"), false)).isTrue();
    }

    @Test
    @DisplayName("SSRF-01b: Loopback addresses are permitted when allowLocalTargets is true")
    void shouldPermitLoopbackWhenAllowed() throws Exception {
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("127.0.0.1"), true)).isFalse();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("::1"), true)).isFalse();
    }

    @Test
    @DisplayName("SSRF-02: RFC 1918 private IPv4 subnets are blocked")
    void shouldBlockRfc1918PrivateAddresses() throws Exception {
        // 10.0.0.0/8
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("10.0.0.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("10.254.1.50"), false)).isTrue();

        // 172.16.0.0/12
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("172.16.0.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("172.31.255.255"), false)).isTrue();

        // 192.168.0.0/16
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("192.168.1.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("192.168.100.200"), false)).isTrue();
    }

    @Test
    @DisplayName("SSRF-03: Cloud metadata (169.254.169.254) and link-local ranges are blocked under all circumstances")
    void shouldBlockCloudMetadataAndLinkLocal() throws Exception {
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("169.254.169.254"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("169.254.169.254"), true)).isTrue(); // blocked even if local is allowed!
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("169.254.1.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("fe80::1"), false)).isTrue();
    }

    @Test
    @DisplayName("SSRF-04: Public Internet addresses are permitted")
    void shouldPermitPublicInternetAddresses() throws Exception {
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("8.8.8.8"), false)).isFalse();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("1.1.1.1"), false)).isFalse();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("93.184.216.34"), false)).isFalse();
    }

    @Test
    @DisplayName("SSRF-05: Multicast, broadcast, and CGNAT ranges are blocked")
    void shouldBlockMulticastAndBroadcast() throws Exception {
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("224.0.0.1"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("255.255.255.255"), false)).isTrue();
        assertThat(cidrBlocklist.isBlocked(InetAddress.getByName("100.64.0.1"), false)).isTrue();
    }
}
