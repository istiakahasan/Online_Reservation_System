package com.example.reservation.reservation.domain.policy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RefundPolicyRegistry {

    private final Map<RefundPolicyType, RefundPolicy> policies;

    public RefundPolicyRegistry(List<RefundPolicy> policyList) {
        this.policies = policyList.stream()
                .collect(Collectors.toMap(RefundPolicy::getPolicyType, Function.identity()));
    }

    public RefundPolicy getPolicy(RefundPolicyType type) {
        if (type == null) {
            return policies.get(RefundPolicyType.FULL);
        }
        return policies.getOrDefault(type, policies.get(RefundPolicyType.FULL));
    }
}
