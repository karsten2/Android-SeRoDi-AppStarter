package com.power.max.serodiappstarter.Services.BeaconLogger;

import org.altbeacon.beacon.Beacon;
import java.util.Comparator;

/**
 * Class to compare to beacons by distance.
 */
public class BeaconDistanceComparator implements Comparator<Beacon> {
    @Override
    public int compare(Beacon b1, Beacon b2) {
        if (b1.getDistance() > b2.getDistance())
            return 1;
        if (b1.getDistance() < b2.getDistance())
            return -1;

        return 0;
    }
}

