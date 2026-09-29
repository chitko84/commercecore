package com.commercecore.payment;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Phase7PaymentTest {
    @Test void approvedMethodsAreExactlyTheSupportedSet() {
        assertThat(PaymentMethod.values()).containsExactly(PaymentMethod.CARD, PaymentMethod.ONLINE_BANKING, PaymentMethod.E_WALLET);
    }
    @Test void paymentStateMachineRejectsInvalidTransitions() {
        assertThatThrownBy(() -> new Payment().paid("x")).isInstanceOf(IllegalStateException.class);
    }
}
