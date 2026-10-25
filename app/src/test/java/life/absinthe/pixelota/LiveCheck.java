package life.absinthe.pixelota;

import java.io.File;

/** Optional network smoke check, separate from deterministic unit tests. */
public class LiveCheck {
    public static void main(String[] args) throws Exception {
        OtaRepository.Result result = new OtaRepository().query(args[0], args[1], new File(args[2]));
        System.out.println("current=" + (result.current == null ? "not found" : result.current.url));
        System.out.println("latest=" + (result.latest == null ? "not found" : result.latest.url));
        System.out.println("betaComplete=" + result.betaComplete);
        for (String warning : result.warnings) System.out.println("warning=" + warning);
        if (result.latest == null || !result.betaComplete || result.current == null) throw new AssertionError("Live source check incomplete");
    }
}
