package parking_lot_mvp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * V1 :
 *  - Ticket
 *  - PricingStrategy
 *      - vehicle type based strategy
 *      - peak hours based pricing ( 10:00 am to 2:00 pm)
 *          - motorcycle : 5
 *          - car : 10
 *          - truck : 20
 *          - peak hours (10am to 2pm) : 1.5x of the price
 MVP Scope:
    - No Gate
    - No Payment Strategy
    - No Concurrency
    - No Spot Strategy  ( nearest to entry/exit gate)
 */
enum VehicleType {
    MOTORCYCLE(List.of(SpotType.SMALL, SpotType.MEDIUM, SpotType.LARGE)),
    CAR(List.of(SpotType.MEDIUM, SpotType.LARGE)),
    TRUCK(List.of(SpotType.LARGE));

    final List<SpotType> spotPreference;

    VehicleType(List<SpotType> spotPreference) {
        this.spotPreference = spotPreference;
    }
}

enum SpotType {
    SMALL,
    MEDIUM,
    LARGE;
}

enum SpotStatus {
    OCCUPIED,
    FREE,
    OUT_OF_ORDER,
    HOLD;
}

public class ParkingLot {

    private final List<ParkingFloor> floors;
    private Map<Vehicle, ParkingSpot> vehicleParkingSpotMap;
    private Set<Ticket> tickets;
    private PricingStrategy pricingStrategy;

    ParkingLot(int numberOfFloors, PricingStrategy pricingStrategy) {
        if(numberOfFloors <= 0) {
            throw new IllegalArgumentException("numberOfFloors cannot be less than or equal to 0");
        }
        Map<SpotType, Integer> capacity = new EnumMap<>(SpotType.class);
        capacity.put(SpotType.SMALL, 1);
        capacity.put(SpotType.MEDIUM, 1);
        capacity.put(SpotType.LARGE, 1);
        floors = new ArrayList<>(numberOfFloors);
        for(int i = 1; i <= numberOfFloors; i++) {
            floors.add(new ParkingFloor(i, capacity));
        }
        vehicleParkingSpotMap = new HashMap<>();
        tickets = new HashSet<>();
        this.pricingStrategy = pricingStrategy;
    }

    record Vehicle (String licensePlate, VehicleType vehicleType) {
        Vehicle {
            Objects.requireNonNull(licensePlate);
            Objects.requireNonNull(vehicleType);
        }
    }

    final class ParkingSpot {
        private final String id;
        private final int floorNumber;
        private final int spotNumber;
        private final SpotType spotType;
        private SpotStatus spotStatus = SpotStatus.FREE;
        private Vehicle vehicle;

        ParkingSpot(int floorNumber, int spotNumber, SpotType spotType) {
            this.floorNumber = floorNumber;
            this.spotNumber = spotNumber;
            this.spotType = spotType;
            this.id = "Type:" + spotType + "F:" + floorNumber + "S:" + spotNumber;
        }

        // method for occupying a Spot
        public void occupySpot(Vehicle vehicle) {
            this.spotStatus = SpotStatus.OCCUPIED;
            this.vehicle = vehicle;
        }

        // method for releasing the spot
        public void releaseSpot() {
            this.spotStatus = SpotStatus.FREE;
            this.vehicle = null;
        }

        SpotStatus getSpotStatus() {
            return spotStatus;
        }

        @Override
        public String toString() {
            return "Floor: " + floorNumber + " , SpotNumber: " + spotNumber + " , SpotType: " + spotType + ", status: " + spotStatus + ", vehicle: " + vehicle;
        }
    }

    final class ParkingFloor{
        final int FloorNumber;
        // mapping of spotid -> ParkingSpot
        final Map<String, ParkingSpot> spots = new LinkedHashMap<>();
        final Map<SpotType, Set<ParkingSpot>> freeParkingSpotsPerType = new EnumMap<>(SpotType.class);

        ParkingFloor(int floorNumber, Map<SpotType, Integer> perSpotTypeCapacity) {
            FloorNumber = floorNumber;
            for(SpotType spotType : perSpotTypeCapacity.keySet()) {
                for(int i = 1; i <= perSpotTypeCapacity.get(spotType); i++) {
                    ParkingSpot spot = new ParkingSpot(floorNumber, i, spotType);
                    spots.put(spot.id, spot);
                    freeParkingSpotsPerType.putIfAbsent(spot.spotType, new HashSet<>());
                    freeParkingSpotsPerType.get(spot.spotType).add(spot);
                }
            }
        }

        //method for free spots
    }

    final class Ticket{
        private final ParkingSpot parkingSpot;
        private final Vehicle vehicle;
        private final Instant entryTime;

        Ticket(ParkingSpot parkingSpot, Vehicle vehicle, Instant entryTime) {
            this.parkingSpot = parkingSpot;
            this.vehicle = vehicle;
            this.entryTime = entryTime;
        }
    }

    interface PricingStrategy {
        long calculateParkingFee(Ticket ticket, Instant exitTime);
    }

    final class FreeStrategy implements PricingStrategy {
        public long calculateParkingFee(Ticket ticket, Instant exitTime) {
            return 0;
        }
    }

    static final class VehicleTypePricingStrategy implements PricingStrategy {
        private final Map<VehicleType, Long> ratePerHour = Map.of(
                VehicleType.MOTORCYCLE, 20L,
                VehicleType.CAR, 50L,
                VehicleType.TRUCK, 100L
        );
        public long calculateParkingFee(Ticket ticket, Instant exitTime) {
            long hours = Duration.between(ticket.entryTime, exitTime).toHours() + 1;
            return hours * ratePerHour.get(ticket.vehicle.vehicleType);
        }
    }

    public Optional<Ticket> park(Vehicle vehicle) {
        boolean parkingDone = false;
        //find first preferred slot and occupy it
        for(ParkingFloor floor : floors) {
            //in each floor find the free parking spot for the type
            for(SpotType spotType : vehicle.vehicleType.spotPreference) {
                Set<ParkingSpot> parkingSpotSet = floor.freeParkingSpotsPerType.get(spotType);
                for(ParkingSpot spot : parkingSpotSet) {
                    if(spot.spotStatus.equals(SpotStatus.FREE)) {
                        spot.occupySpot(vehicle);
                        floor.freeParkingSpotsPerType.get(spotType).remove(spot);
                        vehicleParkingSpotMap.put(vehicle, spot);
                        parkingDone = true;
                        System.out.println(spot);
                        Ticket ticket = new Ticket(spot, vehicle, Instant.now());
                        tickets.add(ticket);
                        return Optional.of(ticket);
                    }
                }
            }
            if(!parkingDone) {
                System.out.println("No free Parking Spots left, Parking Full ");
            }
            //once parking is done, remove that spot from freespots
            //mark that spot as occupied
        }
        //if parking full throw exception
        return Optional.ofNullable(null);
    }

    public long unpark(Ticket ticket) {
        Vehicle vehicle = ticket.vehicle;
        long parkingFee = 0;
        //have vehicle to spot mapping
        //check vehicle exists in that map
        //if(vehicleParkingSpotMap.containsKey(vehicle)) {
        if(tickets.contains(ticket)) {
            //find the spot
            //free the spot ie update spotstatus and add it to the freeParkingSpotsPerType in that floor
            ParkingSpot parkedSpot = vehicleParkingSpotMap.get(vehicle);
            parkedSpot.releaseSpot();
            ParkingFloor parkingFloor = floors.get(parkedSpot.floorNumber-1);
            parkingFloor.freeParkingSpotsPerType.get(parkedSpot.spotType).add(parkedSpot);
            tickets.remove(ticket);
            parkingFee = pricingStrategy.calculateParkingFee(ticket, Instant.now());
            System.out.println(parkedSpot);
        } else {
            System.out.println("Invalid ticket produced!!!");
        }
        //if not parked zthrow exception
        //if park - free the spot
        return parkingFee;
    }

    static void main(String[] args) {
        ParkingLot parkingLot = new ParkingLot(1, new VehicleTypePricingStrategy());
        Vehicle harrier = new Vehicle("KA03AAA", VehicleType.CAR);
        Vehicle ertiga = new Vehicle("KA03ABC", VehicleType.CAR);
        Vehicle baleno = new Vehicle("KAO3NGAAA", VehicleType.CAR);

        Optional<Ticket> harrierTicket = parkingLot.park(harrier);
        Optional<Ticket> ertigaTicket = parkingLot.park(ertiga);
        Optional<Ticket> balenoTicket = parkingLot.park(baleno);
        System.out.println("Parking fee of harrier is : " + parkingLot.unpark(harrierTicket.get()));

        Optional<Ticket> balenoTicket2 = parkingLot.park(baleno);
        Vehicle ather = new Vehicle("KAAAA", VehicleType.MOTORCYCLE);
        Optional<Ticket> atherTicket = parkingLot.park(ather);

        System.out.println("Parking fee of ertiga is : " + parkingLot.unpark(ertigaTicket.get()));
        System.out.println("Parking fee of baleno is : " + parkingLot.unpark(balenoTicket2.get()));
        System.out.println("Parking fee of ather is : " + parkingLot.unpark(atherTicket.get()));
    }
}
