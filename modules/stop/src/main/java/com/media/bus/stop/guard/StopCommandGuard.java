package com.media.bus.stop.guard;

import com.media.bus.contract.security.MemberPrincipal;

public interface StopCommandGuard {

    MemberPrincipal isMemberAuthenticationAdmin(String token);

    void isStopRegistered(String stopId);
}