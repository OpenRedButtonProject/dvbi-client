package org.orbtv.dvbiclient.model;

import java.util.List;
import java.util.Map;

public interface IService {
    List<RelatedMaterial> getRelatedMaterials();
    Map<String, String> getDisplayNames();
    Triplet getTriplet();
}
