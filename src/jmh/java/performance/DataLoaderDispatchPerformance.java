package performance;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 4)
@Fork(1)
public class DataLoaderDispatchPerformance {

    static Owner o1 = new Owner("O-1", "Andi", List.of("P-1", "P-2", "P-3"));
    static Owner o2 = new Owner("O-2", "George", List.of("P-4", "P-5", "P-6"));
    static Owner o3 = new Owner("O-3", "Peppa", List.of("P-7", "P-8", "P-9", "P-10"));
    static Owner o4 = new Owner("O-4", "Alice", List.of("P-11", "P-12"));
    static Owner o5 = new Owner("O-5", "Bob", List.of("P-13"));
    static Owner o6 = new Owner("O-6", "Catherine", List.of("P-14", "P-15", "P-16"));
    static Owner o7 = new Owner("O-7", "David", List.of("P-17"));
    static Owner o8 = new Owner("O-8", "Emma", List.of("P-18", "P-19", "P-20", "P-21"));
    static Owner o9 = new Owner("O-9", "Frank", List.of("P-22"));
    static Owner o10 = new Owner("O-10", "Grace", List.of("P-23", "P-24"));
    static Owner o11 = new Owner("O-11", "Hannah", List.of("P-25", "P-26", "P-27"));
    static Owner o12 = new Owner("O-12", "Ian", List.of("P-28"));
    static Owner o13 = new Owner("O-13", "Jane", List.of("P-29", "P-30"));
    static Owner o14 = new Owner("O-14", "Kevin", List.of("P-31", "P-32", "P-33"));
    static Owner o15 = new Owner("O-15", "Laura", List.of("P-34"));
    static Owner o16 = new Owner("O-16", "Michael", List.of("P-35", "P-36"));
    static Owner o17 = new Owner("O-17", "Nina", List.of("P-37", "P-38", "P-39", "P-40"));
    static Owner o18 = new Owner("O-18", "Oliver", List.of("P-41"));
    static Owner o19 = new Owner("O-19", "Paula", List.of("P-42", "P-43"));
    static Owner o20 = new Owner("O-20", "Quinn", List.of("P-44", "P-45", "P-46"));
    static Owner o21 = new Owner("O-21", "Rachel", List.of("P-47"));
    static Owner o22 = new Owner("O-22", "Steve", List.of("P-48", "P-49"));
    static Owner o23 = new Owner("O-23", "Tina", List.of("P-50", "P-51", "P-52"));
    static Owner o24 = new Owner("O-24", "Uma", List.of("P-53"));
    static Owner o25 = new Owner("O-25", "Victor", List.of("P-54", "P-55"));
    static Owner o26 = new Owner("O-26", "Wendy", List.of("P-56", "P-57", "P-58"));
    static Owner o27 = new Owner("O-27", "Xander", List.of("P-59"));
    static Owner o28 = new Owner("O-28", "Yvonne", List.of("P-60", "P-61"));
    static Owner o29 = new Owner("O-29", "Zach", List.of("P-62", "P-63", "P-64"));
    static Owner o30 = new Owner("O-30", "Willy", List.of("P-65", "P-66", "P-67"));


    static Pet p1 = new Pet("P-1", "Bella", "O-1", List.of("P-2", "P-3", "P-4"));
    static Pet p2 = new Pet("P-2", "Charlie", "O-2", List.of("P-1", "P-5", "P-6"));
    static Pet p3 = new Pet("P-3", "Luna", "O-3", List.of("P-1", "P-2", "P-7", "P-8"));
    static Pet p4 = new Pet("P-4", "Max", "O-1", List.of("P-1", "P-9", "P-10"));
    static Pet p5 = new Pet("P-5", "Lucy", "O-2", List.of("P-2", "P-6"));
    static Pet p6 = new Pet("P-6", "Cooper", "O-3", List.of("P-3", "P-5", "P-7"));
    static Pet p7 = new Pet("P-7", "Daisy", "O-1", List.of("P-4", "P-6", "P-8"));
    static Pet p8 = new Pet("P-8", "Milo", "O-2", List.of("P-3", "P-7", "P-9"));
    static Pet p9 = new Pet("P-9", "Lola", "O-3", List.of("P-4", "P-8", "P-10"));
    static Pet p10 = new Pet("P-10", "Rocky", "O-1", List.of("P-4", "P-9"));
    static Pet p11 = new Pet("P-11", "Buddy", "O-4", List.of("P-12"));
    static Pet p12 = new Pet("P-12", "Bailey", "O-4", List.of("P-11", "P-13"));
    static Pet p13 = new Pet("P-13", "Sadie", "O-5", List.of("P-12"));
    static Pet p14 = new Pet("P-14", "Maggie", "O-6", List.of("P-15"));
    static Pet p15 = new Pet("P-15", "Sophie", "O-6", List.of("P-14", "P-16"));
    static Pet p16 = new Pet("P-16", "Chloe", "O-6", List.of("P-15"));
    static Pet p17 = new Pet("P-17", "Duke", "O-7", List.of("P-18"));
    static Pet p18 = new Pet("P-18", "Riley", "O-8", List.of("P-17", "P-19"));
    static Pet p19 = new Pet("P-19", "Lilly", "O-8", List.of("P-18", "P-20"));
    static Pet p20 = new Pet("P-20", "Zoey", "O-8", List.of("P-19"));
    static Pet p21 = new Pet("P-21", "Oscar", "O-8", List.of("P-22"));
    static Pet p22 = new Pet("P-22", "Toby", "O-9", List.of("P-21", "P-23"));
    static Pet p23 = new Pet("P-23", "Ruby", "O-10", List.of("P-22"));
    static Pet p24 = new Pet("P-24", "Milo", "O-10", List.of("P-25"));
    static Pet p25 = new Pet("P-25", "Finn", "O-11", List.of("P-24", "P-26"));
    static Pet p26 = new Pet("P-26", "Luna", "O-11", List.of("P-25"));
    static Pet p27 = new Pet("P-27", "Ellie", "O-11", List.of("P-28"));
    static Pet p28 = new Pet("P-28", "Harley", "O-12", List.of("P-27", "P-29"));
    static Pet p29 = new Pet("P-29", "Penny", "O-13", List.of("P-28"));
    static Pet p30 = new Pet("P-30", "Hazel", "O-13", List.of("P-31"));
    static Pet p31 = new Pet("P-31", "Gus", "O-14", List.of("P-30", "P-32"));
    static Pet p32 = new Pet("P-32", "Dexter", "O-14", List.of("P-31"));
    static Pet p33 = new Pet("P-33", "Winnie", "O-14", List.of("P-34"));
    static Pet p34 = new Pet("P-34", "Murphy", "O-15", List.of("P-33", "P-35"));
    static Pet p35 = new Pet("P-35", "Moose", "O-16", List.of("P-34"));
    static Pet p36 = new Pet("P-36", "Scout", "O-16", List.of("P-37"));
    static Pet p37 = new Pet("P-37", "Rex", "O-17", List.of("P-36", "P-38"));
    static Pet p38 = new Pet("P-38", "Coco", "O-17", List.of("P-37"));
    static Pet p39 = new Pet("P-39", "Maddie", "O-17", List.of("P-40"));
    static Pet p40 = new Pet("P-40", "Archie", "O-17", List.of("P-39", "P-41"));
    static Pet p41 = new Pet("P-41", "Buster", "O-18", List.of("P-40"));
    static Pet p42 = new Pet("P-42", "Rosie", "O-19", List.of("P-43"));
    static Pet p43 = new Pet("P-43", "Molly", "O-19", List.of("P-42", "P-44"));
    static Pet p44 = new Pet("P-44", "Henry", "O-20", List.of("P-43"));
    static Pet p45 = new Pet("P-45", "Leo", "O-20", List.of("P-46"));
    static Pet p46 = new Pet("P-46", "Jack", "O-20", List.of("P-45", "P-47"));
    static Pet p47 = new Pet("P-47", "Zoe", "O-21", List.of("P-46"));
    static Pet p48 = new Pet("P-48", "Lulu", "O-22", List.of("P-49"));
    static Pet p49 = new Pet("P-49", "Mimi", "O-22", List.of("P-48", "P-50"));
    static Pet p50 = new Pet("P-50", "Nala", "O-23", List.of("P-49"));
    static Pet p51 = new Pet("P-51", "Simba", "O-23", List.of("P-52"));
    static Pet p52 = new Pet("P-52", "Teddy", "O-23", List.of("P-51", "P-53"));
    static Pet p53 = new Pet("P-53", "Mochi", "O-24", List.of("P-52"));
    static Pet p54 = new Pet("P-54", "Oreo", "O-25", List.of("P-55"));
    static Pet p55 = new Pet("P-55", "Peanut", "O-25", List.of("P-54", "P-56"));
    static Pet p56 = new Pet("P-56", "Pumpkin", "O-26", List.of("P-55"));
    static Pet p57 = new Pet("P-57", "Shadow", "O-26", List.of("P-58"));
    static Pet p58 = new Pet("P-58", "Sunny", "O-26", List.of("P-57", "P-59"));
    static Pet p59 = new Pet("P-59", "Thor", "O-27", List.of("P-58"));
    static Pet p60 = new Pet("P-60", "Willow", "O-28", List.of("P-61"));
    static Pet p61 = new Pet("P-61", "Zeus", "O-28", List.of("P-60", "P-62"));
    static Pet p62 = new Pet("P-62", "Ace", "O-29", List.of("P-61"));
    static Pet p63 = new Pet("P-63", "Blue", "O-29", List.of("P-64"));
    static Pet p64 = new Pet("P-64", "Cleo", "O-29", List.of("P-63", "P-65"));
    static Pet p65 = new Pet("P-65", "Dolly", "O-30", List.of("P-64"));
    static Pet p66 = new Pet("P-66", "Ella", "O-30", List.of("P-67"));
    static Pet p67 = new Pet("P-67", "Freddy", "O-30", List.of("P-66"));


    static Map<String, Owner> owners = Map.ofEntries(
            Map.entry(o1.id, o1),
            Map.entry(o2.id, o2),
            Map.entry(o3.id, o3),
            Map.entry(o4.id, o4),
            Map.entry(o5.id, o5),
            Map.entry(o6.id, o6),
            Map.entry(o7.id, o7),
            Map.entry(o8.id, o8),
            Map.entry(o9.id, o9),
            Map.entry(o10.id, o10),
            Map.entry(o11.id, o11),
            Map.entry(o12.id, o12),
            Map.entry(o13.id, o13),
            Map.entry(o14.id, o14),
            Map.entry(o15.id, o15),
            Map.entry(o16.id, o16),
            Map.entry(o17.id, o17),
            Map.entry(o18.id, o18),
            Map.entry(o19.id, o19),
            Map.entry(o20.id, o20),
            Map.entry(o21.id, o21),
            Map.entry(o22.id, o22),
            Map.entry(o23.id, o23),
            Map.entry(o24.id, o24),
            Map.entry(o25.id, o25),
            Map.entry(o26.id, o26),
            Map.entry(o27.id, o27),
            Map.entry(o28.id, o28),
            Map.entry(o29.id, o29),
            Map.entry(o30.id, o30)
    );
    static Map<String, Pet> pets = Map.ofEntries(
            Map.entry(p1.id, p1),
            Map.entry(p2.id, p2),
            Map.entry(p3.id, p3),
            Map.entry(p4.id, p4),
            Map.entry(p5.id, p5),
            Map.entry(p6.id, p6),
            Map.entry(p7.id, p7),
            Map.entry(p8.id, p8),
            Map.entry(p9.id, p9),
            Map.entry(p10.id, p10),
            Map.entry(p11.id, p11),
            Map.entry(p12.id, p12),
            Map.entry(p13.id, p13),
            Map.entry(p14.id, p14),
            Map.entry(p15.id, p15),
            Map.entry(p16.id, p16),
            Map.entry(p17.id, p17),
            Map.entry(p18.id, p18),
            Map.entry(p19.id, p19),
            Map.entry(p20.id, p20),
            Map.entry(p21.id, p21),
            Map.entry(p22.id, p22),
            Map.entry(p23.id, p23),
            Map.entry(p24.id, p24),
            Map.entry(p25.id, p25),
            Map.entry(p26.id, p26),
            Map.entry(p27.id, p27),
            Map.entry(p28.id, p28),
            Map.entry(p29.id, p29),
            Map.entry(p30.id, p30),
            Map.entry(p31.id, p31),
            Map.entry(p32.id, p32),
            Map.entry(p33.id, p33),
            Map.entry(p34.id, p34),
            Map.entry(p35.id, p35),
            Map.entry(p36.id, p36),
            Map.entry(p37.id, p37),
            Map.entry(p38.id, p38),
            Map.entry(p39.id, p39),
            Map.entry(p40.id, p40),
            Map.entry(p41.id, p41),
            Map.entry(p42.id, p42),
            Map.entry(p43.id, p43),
            Map.entry(p44.id, p44),
            Map.entry(p45.id, p45),
            Map.entry(p46.id, p46),
            Map.entry(p47.id, p47),
            Map.entry(p48.id, p48),
            Map.entry(p49.id, p49),
            Map.entry(p50.id, p50),
            Map.entry(p51.id, p51),
            Map.entry(p52.id, p52),
            Map.entry(p53.id, p53),
            Map.entry(p54.id, p54),
            Map.entry(p55.id, p55),
            Map.entry(p56.id, p56),
            Map.entry(p57.id, p57),
            Map.entry(p58.id, p58),
            Map.entry(p59.id, p59),
            Map.entry(p60.id, p60),
            Map.entry(p61.id, p61),
            Map.entry(p62.id, p62),
            Map.entry(p63.id, p63),
            Map.entry(p64.id, p64),
            Map.entry(p65.id, p65),
            Map.entry(p66.id, p66),
            Map.entry(p67.id, p67)
    );

    static class Owner {
        public Owner(String id, String name, List<String> petIds) {
            this.id = id;
            this.name = name;
            this.petIds = petIds;
        }

        String id;
        String name;
        List<String> petIds;
    }

    static class Pet {
        public Pet(String id, String name, String ownerId, List<String> friendsIds) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.friendsIds = friendsIds;
        }

        String id;
        String name;
        String ownerId;
        List<String> friendsIds;
    }


    static BatchLoader<String, Owner> ownerBatchLoader = list -> {
        List<Owner> collect = list.stream().map(key -> {
            Owner owner = owners.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };
    static BatchLoader<String, Pet> petBatchLoader = list -> {
        List<Pet> collect = list.stream().map(key -> {
            Pet owner = pets.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };


    @State(Scope.Benchmark)
    public static class MyState {
        @Setup
        public void setup() {

        }

        DataLoader ownerDL = DataLoaderFactory.newDataLoader(ownerBatchLoader);
        DataLoader petDL = DataLoaderFactory.newDataLoader(petBatchLoader);


    }


    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Threads(Threads.MAX)
    public void loadAndDispatch(MyState myState, Blackhole blackhole) {
        DataLoader ownerDL = myState.ownerDL;
        DataLoader petDL = myState.petDL;

        for (Owner owner : owners.values()) {
            ownerDL.load(owner.id);
            for (String petId : owner.petIds) {
                petDL.load(petId);
                for (String friendId : pets.get(petId).friendsIds) {
                    petDL.load(friendId);
                }
            }
        }

        CompletableFuture cf1 = ownerDL.dispatch();
        CompletableFuture cf2 = petDL.dispatch();
        blackhole.consume(CompletableFuture.allOf(cf1, cf2).join());
    }


}
