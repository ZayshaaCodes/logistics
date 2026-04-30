package com.logistics.power.cable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CableTierTest {
    @Test
    void cableTiersUseExpectedTransferRates() {
        assertThat(CableTier.COPPER.transferRate()).isEqualTo(30);
        assertThat(CableTier.GOLD.transferRate()).isEqualTo(60);
        assertThat(CableTier.ENDER.transferRate()).isEqualTo(120);
    }
}
