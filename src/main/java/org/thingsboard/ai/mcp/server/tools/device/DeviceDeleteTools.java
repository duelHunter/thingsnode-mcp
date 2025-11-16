package org.thingsboard.ai.mcp.server.tools.device;

import org.thingsboard.ai.mcp.server.tools.McpTools;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.thingsboard.ai.mcp.server.rest.RestClientService;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.DeviceId;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DeviceDeleteTools implements McpTools {

    private final RestClientService clientService;

    /**
     * Delete a single device by deviceId.
     *
     * @param deviceId UUID string of the device to delete
     * @param dryRun   if true, will not perform the delete but will return what would happen
     * @return JSON string with result
     */
    @Tool(description = "Delete a device by UUID. Use dryRun=true to preview without deleting.")
    public String deleteDevice(
            @ToolParam(description = "Device UUID to delete (e.g. 0fa21ab0-a1e5-11f0-a6ac-1b1f3daec56d)") @NotBlank String deviceId,
            @ToolParam(required = false, description = "If true, perform a dry run (no deletion)") Boolean dryRun
    ) {
        boolean isDry = Boolean.TRUE.equals(dryRun);
        var client = clientService.getClient();
        try {
            UUID did = UUID.fromString(deviceId);
            // Try fetch - to give better error messages and to return device information
            var deviceOpt = client.getDeviceById(new DeviceId(did)); // many RestClient variants return Optional<Device>
            Object deviceObj = null;
            if (deviceOpt instanceof Optional) {
                Optional<?> o = (Optional<?>) deviceOpt;
                if (o.isPresent()) deviceObj = o.get();
            } else {
                // Some RestClient implementations return the entity directly or null
                deviceObj = deviceOpt;
            }

            if (deviceObj == null) {
                return JacksonUtil.toString(Map.of(
                        "ok", false,
                        "error", "DeviceNotFound",
                        "deviceId", deviceId,
                        "message", "Device not found"
                ));
            }

            // Build a friendly preview of device (serialize)
            String deviceJson = JacksonUtil.toString(deviceObj);

            if (isDry) {
                return JacksonUtil.toString(Map.of(
                        "ok", true,
                        "dryRun", true,
                        "deviceId", deviceId,
                        "device", JacksonUtil.fromString(deviceJson, Object.class),
                        "message", "Dry run - no deletion performed"
                ));
            }
            client.deleteDevice(new DeviceId(did));

            return JacksonUtil.toString(Map.of(
                    "ok", true,
                    "deviceId", deviceId,
                    "device", JacksonUtil.fromString(deviceJson, Object.class),
                    "message", "Device deleted"
            ));
        } catch (IllegalArgumentException iae) {
            return JacksonUtil.toString(Map.of(
                    "ok", false,
                    "error", "InvalidUUID",
                    "message", "Provided deviceId is not a valid UUID: " + deviceId
            ));
        } catch (Exception e) {
            return JacksonUtil.toString(Map.of(
                    "ok", false,
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete multiple devices by comma-separated device UUIDs.
     * The method will attempt to delete each device independently, and return a per-device result summary.
     *
     * @param deviceIdsCsv comma-separated UUIDs
     * @param dryRun       if true, only preview
     */
    @Tool(description = "Delete multiple devices by comma-separated UUIDs. Returns per-device results. Use dryRun=true to preview.")
    public String deleteDevices(
            @ToolParam(description = "Comma-separated list of device UUIDs (or a JSON array string)") @NotBlank String deviceIdsCsv,
            @ToolParam(required = false, description = "Dry run = true to preview") Boolean dryRun
    ) {
        boolean isDry = Boolean.TRUE.equals(dryRun);
        // Normalize input: allow JSON array or CSV
        List<String> ids;
        try {
            if (deviceIdsCsv.trim().startsWith("[")) {
                // JSON array
                List<?> parsed = JacksonUtil.fromString(deviceIdsCsv, List.class);
                ids = parsed.stream().map(Object::toString).collect(Collectors.toList());
            } else {
                ids = Stream.of(deviceIdsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            return JacksonUtil.toString(Map.of(
                    "ok", false,
                    "error", "InvalidInput",
                    "message", "Could not parse deviceIdsCsv. Send comma-separated UUIDs or a JSON array string."
            ));
        }

        var results = new ArrayList<Map<String, Object>>();
        for (String id : ids) {
            String res;
            try {
                String resp = deleteDevice(id, isDry);
                // deleteDevice already returns JSON string; convert to object for aggregation
                Object parsed = JacksonUtil.fromString(resp, Object.class);
                results.add(Map.of("deviceId", id, "result", parsed));
            } catch (Exception e) {
                results.add(Map.of("deviceId", id, "result", Map.of("ok", false, "error", e.getClass().getSimpleName(), "message", e.getMessage())));
            }
        }

        return JacksonUtil.toString(Map.of(
                "ok", true,
                "dryRun", isDry,
                "summary", Map.of(
                        "requested", ids.size(),
                        "processed", results.size()
                ),
                "results", results
        ));
    }
}
