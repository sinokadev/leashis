package dev.sinoka.leashis.leash;

/** Why a connection ended; item ownership is tracked independently by the session. */
public enum DetachReason {
    MANUAL,
    TOO_FAR,
    DEATH,
    LOGOUT,
    DIMENSION_CHANGE,
    RIDING,
    DISABLED,
    PROXY_REMOVED,
    SERVER_STOP
}
