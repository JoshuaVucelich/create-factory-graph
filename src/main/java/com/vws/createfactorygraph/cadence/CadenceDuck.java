package com.vws.createfactorygraph.cadence;

/** Added to every Create SmartBlockEntity. gate == null means ground: never gated, never looks anything up. */
public interface CadenceDuck {
    ShipGate cfg$gate();
    void cfg$setGate(ShipGate gate);
    int cfg$debt();
    void cfg$setDebt(int debt);
}
