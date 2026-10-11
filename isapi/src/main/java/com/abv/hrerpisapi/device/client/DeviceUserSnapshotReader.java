package com.abv.hrerpisapi.device.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;

/** A partial search must never be used to authorize employee removal. */
final class DeviceUserSnapshotReader {
    @FunctionalInterface
    interface PageLoader { JsonNode load(int position) throws IOException, InterruptedException; }

    static List<IsapiClient.DevicePersonInfo> read(PageLoader loader) throws IOException, InterruptedException {
        List<IsapiClient.DevicePersonInfo> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        Integer expectedTotal = null;
        for (int page = 0; page < 3334; page++) {
            JsonNode root = loader.load(result.size());
            if (root == null) throw new IOException("Empty user search response");
            JsonNode search = root.has("UserInfoSearch") ? root.path("UserInfoSearch") : root;
            String status = search.path("responseStatusStrg").asText("").toUpperCase(Locale.ROOT);
            JsonNode users = search.path("UserInfo");
            int matches = search.path("numOfMatches").asInt(-1);
            int total = search.path("totalMatches").asInt(-1);
            if (matches < 0 || total < 0 || (expectedTotal != null && expectedTotal != total)
                    || (!users.isArray() && !(matches == 0 && users.isMissingNode()))
                    || (users.isArray() && users.size() != matches)
                    || !(status.equals("OK") || status.equals("MORE") || status.equals("NO MATCH"))) {
                throw new IOException("Incomplete or inconsistent user search response");
            }
            expectedTotal = total;
            for (JsonNode user : users) {
                String no = user.path("employeeNo").asText("").trim();
                if (no.isEmpty() || !identities.add(no)) throw new IOException("Ambiguous user search identity");
                result.add(new IsapiClient.DevicePersonInfo(no, user.path("name").asText(null),
                        user.path("userType").asText(null), user.path("gender").asText(null),
                        user.path("Valid").path("beginTime").asText(null), user.path("Valid").path("endTime").asText(null)));
            }
            if (!status.equals("MORE")) {
                if (result.size() != total || (status.equals("NO MATCH") && matches != 0))
                    throw new IOException("User search ended before all users were returned");
                return List.copyOf(result);
            }
            if (matches == 0 || result.size() >= total) throw new IOException("User search did not advance");
        }
        throw new IOException("User search page limit exceeded");
    }
}
