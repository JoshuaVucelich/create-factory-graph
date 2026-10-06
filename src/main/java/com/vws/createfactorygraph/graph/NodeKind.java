package com.vws.createfactorygraph.graph;

public enum NodeKind {
    /** Generates rotation (motors, water wheels, engines...). Always ticks. */
    SOURCE,
    /** Decorative relay with no inventory (shaft, cog, gearbox...). Dormant once settled. */
    RELAY,
    /** Belt controller: owns the belt inventory; ticks every tick to keep cadence (backs off only when jammed). */
    BELT_CONTROLLER,
    /** Non-controller belt segment: advanced by its controller; dormant once settled. */
    BELT_SEGMENT,
    /** Any other kinetic consumer (press, mixer, saw, fan, deployer...). Ticks normally. */
    MACHINE,
    /** Non-kinetic logistics endpoint attached to a belt end (chute, depot, funnel, belt input...). */
    LOGISTICS
}
