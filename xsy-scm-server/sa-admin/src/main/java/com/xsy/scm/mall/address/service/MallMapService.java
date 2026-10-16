package com.xsy.scm.mall.address.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 固定高德 Web 服务域名，Key 仅保留在服务端环境变量中。 */
@Service
@RequiredArgsConstructor
public class MallMapService {
    private static final String BASE = "https://restapi.amap.com/v3";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper objectMapper;

    @Value("${XSY_AMAP_WEB_SERVICE_KEY:}")
    private String webServiceKey;

    public List<MallMapLocationVO> search(String keyword, String city) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 100) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        String path = "/place/text?keywords=" + encode(keyword.trim()) + "&offset=20&page=1&extensions=base";
        if (city != null && !city.isBlank()) {
            path += "&city=" + encode(city.trim()) + "&citylimit=true";
        }
        JsonNode pois = request(path).path("pois");
        List<MallMapLocationVO> result = new ArrayList<>();
        if (pois.isArray()) {
            for (JsonNode poi : pois) {
                MallMapLocationVO location = location(poi.path("location").asText(null),
                        poi.path("adcode").asText(null));
                if (location == null)
                    continue;
                location.setName(poi.path("name").asText(null));
                location.setAddress(poi.path("address").asText(null));
                location.setProvinceName(poi.path("pname").asText(null));
                location.setCityName(textOr(poi.path("cityname"), location.getProvinceName()));
                location.setDistrictName(poi.path("adname").asText(null));
                result.add(location);
            }
        }
        return result;
    }

    public MallMapLocationVO reverse(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null || longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0 || latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        String coordinate = longitude.setScale(6, RoundingMode.HALF_UP).toPlainString() + ","
                + latitude.setScale(6, RoundingMode.HALF_UP).toPlainString();
        JsonNode root = request("/geocode/regeo?location=" + encode(coordinate) + "&extensions=base");
        JsonNode regeo = root.path("regeocode");
        JsonNode component = regeo.path("addressComponent");
        MallMapLocationVO location = location(coordinate, component.path("adcode").asText(null));
        if (location == null || location.getDistrictCode() == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        location.setLongitude(longitude);
        location.setLatitude(latitude);
        location.setAddress(regeo.path("formatted_address").asText(null));
        location.setProvinceName(component.path("province").asText(null));
        location.setCityName(textOr(component.path("city"), location.getProvinceName()));
        location.setDistrictName(component.path("district").asText(null));
        return location;
    }

    private JsonNode request(String path) {
        if (webServiceKey == null || webServiceKey.isBlank()) {
            throw new ScmBusinessException(MallErrorCode.MAP_UNAVAILABLE);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path + "&key=" + encode(webServiceKey)))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ScmBusinessException(MallErrorCode.MAP_UNAVAILABLE);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"1".equals(root.path("status").asText())) {
                throw new ScmBusinessException(MallErrorCode.MAP_UNAVAILABLE);
            }
            return root;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ScmBusinessException(MallErrorCode.MAP_UNAVAILABLE);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new ScmBusinessException(MallErrorCode.MAP_UNAVAILABLE);
        }
    }

    private static MallMapLocationVO location(String coordinates, String adcode) {
        if (coordinates == null || !coordinates.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?"))
            return null;
        String[] values = coordinates.split(",", 2);
        MallMapLocationVO result = new MallMapLocationVO();
        result.setLongitude(new BigDecimal(values[0]));
        result.setLatitude(new BigDecimal(values[1]));
        if (adcode != null && adcode.matches("\\d{6}")) {
            result.setDistrictCode(Integer.valueOf(adcode));
            result.setProvinceCode(Integer.valueOf(adcode.substring(0, 2) + "0000"));
            result.setCityCode(Integer.valueOf(adcode.substring(0, 4) + "00"));
        }
        return result;
    }

    private static String textOr(JsonNode node, String fallback) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
