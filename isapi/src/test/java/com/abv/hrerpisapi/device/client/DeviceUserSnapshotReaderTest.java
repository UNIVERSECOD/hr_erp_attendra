package com.abv.hrerpisapi.device.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceUserSnapshotReaderTest {
    ObjectMapper mapper = new ObjectMapper();
    @Test void continuesShortMorePageUntilExplicitCompleteTotal() throws Exception {
        List<Integer> positions = new ArrayList<>();
        var result = DeviceUserSnapshotReader.read(position -> {
            positions.add(position);
            return mapper.readTree(position == 0
                    ? "{\"UserInfoSearch\":{\"responseStatusStrg\":\"MORE\",\"numOfMatches\":1,\"totalMatches\":2,\"UserInfo\":[{\"employeeNo\":\"1\"}]}}"
                    : "{\"UserInfoSearch\":{\"responseStatusStrg\":\"OK\",\"numOfMatches\":1,\"totalMatches\":2,\"UserInfo\":[{\"employeeNo\":\"2\"}]}}");
        });
        assertEquals(List.of(0, 1), positions); assertEquals(2, result.size());
    }
    @Test void acceptsExplicitEmptyList() throws Exception {
        assertTrue(DeviceUserSnapshotReader.read(p -> mapper.readTree("{\"responseStatusStrg\":\"NO MATCH\",\"numOfMatches\":0,\"totalMatches\":0}")).isEmpty());
    }
    @Test void rejectsMalformedIncompleteAndErrorResponses() {
        for (String json : List.of("{}", "null", "{\"statusCode\":4}",
                "{\"responseStatusStrg\":\"OK\",\"numOfMatches\":0,\"totalMatches\":2}",
                "{\"responseStatusStrg\":\"MORE\",\"numOfMatches\":0,\"totalMatches\":2}",
                "{\"responseStatusStrg\":\"OK\",\"numOfMatches\":1,\"totalMatches\":1,\"UserInfo\":[{}]}")) {
            assertThrows(IOException.class, () -> DeviceUserSnapshotReader.read(p -> mapper.readTree(json)), json);
        }
    }
    @Test void rejectsRepeatedPagesAndChangingTotal() {
        assertThrows(IOException.class, () -> DeviceUserSnapshotReader.read(p -> mapper.readTree(
                "{\"responseStatusStrg\":\"MORE\",\"numOfMatches\":1,\"totalMatches\":3,\"UserInfo\":[{\"employeeNo\":\"1\"}]}")));
        assertThrows(IOException.class, () -> DeviceUserSnapshotReader.read(p -> mapper.readTree(
                "{\"responseStatusStrg\":\"MORE\",\"numOfMatches\":1,\"totalMatches\":" + (3+p) + ",\"UserInfo\":[{\"employeeNo\":\"" + p + "\"}]}")));
    }
}
