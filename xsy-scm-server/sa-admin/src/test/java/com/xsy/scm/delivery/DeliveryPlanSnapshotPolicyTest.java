package com.xsy.scm.delivery;

import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanProposalVO;
import com.xsy.scm.delivery.support.DeliveryPlanSnapshotPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 建议历史使用冻结快照，不把后来编辑的站点/地址/版本伪装成旧建议。 */
class DeliveryPlanSnapshotPolicyTest {

    @Test
    void frozenRouteSourceAndOriginalOrderSurviveLaterEdits() {
        var route = new DeliveryRouteEntity();
        route.setVersion(5);
        route.setWarehouseId(8L);
        route.setDeliveryDate(LocalDate.of(2026, 10, 17));
        route.setStatus("DRAFT");
        route.setStartLongitude(new BigDecimal("108.940000"));
        route.setStartLatitude(new BigDecimal("34.340000"));
        route.setStartGeomCrs("GCJ02");

        var first = stop(91L, 1, "旧址甲", "108.95", "34.35");
        var second = stop(92L, 2, "旧址乙", "108.96", "34.36");
        var proposal = new DeliveryPlanProposalEntity();
        proposal.setInputSnapshot(DeliveryPlanSnapshotPolicy.capture(route, List.of(first, second)));
        proposal.setResultSnapshot(Map.of("legs", List.of(
                Map.of("seq", 1, "stopId", 92L, "legDistance", "100.0000", "cumulativeDistance", "100.0000"),
                Map.of("seq", 2, "stopId", 91L, "legDistance", "200.0000", "cumulativeDistance", "300.0000"))));

        route.setVersion(6);
        route.setStartLongitude(new BigDecimal("110.0000"));
        first.setAddressSnapshot("新址甲");
        first.setStopSeq(2);

        var view = new DeliveryPlanProposalVO();
        DeliveryPlanSnapshotPolicy.fillPreviewSource(proposal, view);
        var legs = DeliveryPlanSnapshotPolicy.legs(proposal);
        assertThat(view.getSourceRouteVersion()).isEqualTo(5);
        assertThat(view.getSourceLongitude()).isEqualByComparingTo("108.94");
        assertThat(view.getSourceGeomCrs()).isEqualTo("GCJ02");
        assertThat(view.getIgnoredFactors()).containsExactly("TIME_WINDOW", "VEHICLE_CAPACITY", "TRAFFIC");
        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).getStopId()).isEqualTo(92L);
        assertThat(legs.get(0).getSeq()).isEqualTo(1);
        assertThat(legs.get(0).getOriginalSeq()).isEqualTo(2);
        assertThat(legs.get(1).getOriginalSeq()).isEqualTo(1);
        assertThat(legs.get(1).getAddressSnapshot()).isEqualTo("旧址甲");
    }

    @Test
    void oldProposalWithoutPreviewMetadataFailsClosedOnPreview() {
        var proposal = new DeliveryPlanProposalEntity();
        proposal.setInputSnapshot(Map.of("routeVersion", "not-a-number"));
        var view = new DeliveryPlanProposalVO();
        DeliveryPlanSnapshotPolicy.fillPreviewSource(proposal, view);
        assertThat(view.getSourceRouteVersion()).isNull();
        assertThat(view.getIgnoredFactors()).isEmpty();
    }

    private static DeliveryRouteStopEntity stop(Long id, int seq, String address, String lon, String lat) {
        var row = new DeliveryRouteStopEntity();
        row.setId(id);
        row.setVersion(0);
        row.setStopSeq(seq);
        row.setCustomerNameSnapshot(address);
        row.setAddressSnapshot(address);
        row.setLongitude(new BigDecimal(lon));
        row.setLatitude(new BigDecimal(lat));
        row.setGeomCrs("GCJ02");
        return row;
    }
}
