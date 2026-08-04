package com.autobanrobot.server.client;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class IpGeolocationService {

    private final DatabaseReader database;

    public IpGeolocationService(@Value("${autoban.geoip.database:}") String databasePath) {
        this.database = openDatabase(databasePath);
    }

    public IpLocation locate(String clientIp) {
        if (database == null || clientIp == null || clientIp.isBlank()) return IpLocation.unresolved();
        try {
            CityResponse response = database.city(InetAddress.getByName(clientIp));
            Double latitude = response.getLocation().getLatitude();
            Double longitude = response.getLocation().getLongitude();
            if (latitude == null || longitude == null) return IpLocation.unresolved();
            String city = response.getCity().getName();
            String country = response.getCountry().getName();
            String label = Stream.of(city, country).filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
            return label.isBlank() ? IpLocation.unresolved() : new IpLocation(label, latitude, longitude);
        } catch (IOException | GeoIp2Exception ignored) {
            return IpLocation.unresolved();
        }
    }

    private DatabaseReader openDatabase(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) return null;
        try {
            if (databasePath.startsWith("classpath:")) {
                ClassPathResource resource = new ClassPathResource(databasePath.substring("classpath:".length()));
                if (!resource.exists()) return null;
                try (InputStream stream = resource.getInputStream()) {
                    return new DatabaseReader.Builder(stream).build();
                }
            }
            Path path = Path.of(databasePath);
            return Files.isRegularFile(path) ? new DatabaseReader.Builder(path.toFile()).build() : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
