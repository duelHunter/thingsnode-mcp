package org.thingsboard.ai.mcp.server.tools.relation;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.thingsboard.ai.mcp.server.rest.RestClientService;
import org.thingsboard.ai.mcp.server.tools.McpTools;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationInfo;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetDeviceRelationTools implements McpTools {

    private final RestClientService clientService;

    @Tool(description = "Assign a device to an asset by creating the relation ASSET --Contains--> DEVICE. " +
            "Returns JSON with created=true if a new relation was made, or created=false if it already existed.")
    public String assignDeviceToAsset(
            @ToolParam(description = "Asset UUID") @NotBlank String assetId,
            @ToolParam(description = "Device UUID") @NotBlank String deviceId,
            @ToolParam(required = false, description = "Relation type (default 'Contains')") String relationType
    ) {
        var client = clientService.getClient();
        var fromId = new AssetId(UUID.fromString(assetId));
        var toId   = new DeviceId(UUID.fromString(deviceId));
        var type   = (relationType == null || relationType.isBlank()) ? "Contains" : relationType;

        try {
            // ✅ pass RelationTypeGroup.COMMON explicitly
            boolean exists = relationExists(fromId, toId, type, RelationTypeGroup.COMMON);
            if (!exists) {
                var rel = new EntityRelation(fromId, toId, type);
                rel.setTypeGroup(RelationTypeGroup.COMMON);
                client.saveRelation(rel); // POST /api/relation
            }

            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("fromType", EntityType.ASSET.name());
            result.put("fromId", assetId);
            result.put("toType", EntityType.DEVICE.name());
            result.put("toId", deviceId);
            result.put("type", type);
            result.put("typeGroup", RelationTypeGroup.COMMON.name());
            result.put("created", !exists);
            return JacksonUtil.toString(result);

        } catch (Exception e) {
            var error = new java.util.LinkedHashMap<String, Object>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            error.put("fromId", assetId);
            error.put("toId", deviceId);
            return JacksonUtil.toString(error);
        }
    }

    @Tool(description = "List relation infos originating from an Asset (same as GET /api/relations/info?fromId=...&fromType=ASSET).")
    public String getRelationsFromAsset(
            @ToolParam(description = "Asset UUID") @NotBlank String assetId,
            @ToolParam(required = false, description = "Relation type group (COMMON, ALARM, etc). Default COMMON")
            String relationTypeGroup
    ) {
        var client = clientService.getClient();
        try {
            var from = new AssetId(UUID.fromString(assetId));
            var group = (relationTypeGroup == null || relationTypeGroup.isBlank())
                    ? RelationTypeGroup.COMMON
                    : RelationTypeGroup.valueOf(relationTypeGroup);

            // ✅ now calling the correct overload with 2 args
            List<EntityRelationInfo> infos = client.findInfoByFrom(from, group);
            return JacksonUtil.toString(infos);

        } catch (Exception e) {
            var error = new java.util.LinkedHashMap<String, Object>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            error.put("fromId", assetId);
            return JacksonUtil.toString(error);
        }
    }

    /** Helper: check if a specific relation already exists. */
    private boolean relationExists(EntityId from, EntityId to, String type, RelationTypeGroup group) {
        var client = clientService.getClient();
        try {
            // ✅ use the 2-arg method here as well
            List<EntityRelationInfo> infos = client.findInfoByFrom(from, group);
            if (infos == null) return false;
            for (var info : infos) {
                var toInfo = info.getTo();
                if (toInfo != null
                        && toInfo.getEntityType() == to.getEntityType()
                        && toInfo.getId().equals(to.getId())
                        && type.equals(info.getType())
                        && group == info.getTypeGroup()) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}