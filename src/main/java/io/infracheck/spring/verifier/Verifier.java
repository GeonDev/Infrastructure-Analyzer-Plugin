package io.infracheck.spring.verifier;

import io.infracheck.core.model.Requirements;
import io.infracheck.spring.model.VerificationResult;

import java.util.List;

public interface Verifier {
    List<VerificationResult> verify(Requirements requirements);
}
