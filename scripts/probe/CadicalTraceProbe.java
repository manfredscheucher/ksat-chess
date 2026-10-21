// Throwaway probe: solve a DIMACS with the Kotlin CaDiCaL port, print timings,
// trace line count and a hash of the full trace (to compare solver behaviour across builds).
// Usage: java -cp <jars> scripts/probe/CadicalTraceProbe.java <file.cnf> [notrace]
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.bytefred.ksat.cadical.CaDiCaL;
import org.bytefred.ksat.cadical.ActivityPrecision;

public class CadicalTraceProbe {
    public static void main(String[] a) throws Exception {
        boolean trace = !(a.length > 1 && a[1].equals("notrace"));
        List<int[]> clauses = new ArrayList<>();
        int nv = 0;
        try (BufferedReader r = Files.newBufferedReader(Path.of(a[0]))) {
            String line; List<Integer> cur = new ArrayList<>();
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("c")) continue;
                if (line.startsWith("p")) { nv = Integer.parseInt(line.split("\\s+")[2]); continue; }
                for (String t : line.split("\\s+")) {
                    int l = Integer.parseInt(t);
                    if (l == 0) { clauses.add(cur.stream().mapToInt(Integer::intValue).toArray()); cur.clear(); }
                    else cur.add(l);
                }
            }
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long[] lines = {0};
        long t0 = System.nanoTime();
        CaDiCaL s = new CaDiCaL(0, ActivityPrecision.FLOAT64, 25L, 2L, 1000L);
        if (trace) s.setTraceSink(x -> { md.update(x.getBytes()); md.update((byte) '\n'); lines[0]++; return kotlin.Unit.INSTANCE; });
        for (int[] c : clauses) s.addClause(c);
        long t1 = System.nanoTime();
        Object res = s.solve();
        long t2 = System.nanoTime();
        System.out.printf("vars=%d clauses=%d result=%s setup=%dms solve=%dms traceLines=%d sha=%s%n",
            nv, clauses.size(), res, (t1 - t0) / 1000000, (t2 - t1) / 1000000, lines[0],
            trace ? HexFormat.of().formatHex(md.digest()) : "-");
    }
}
