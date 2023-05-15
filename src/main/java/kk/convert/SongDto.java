package kk.convert;

import lombok.Builder;

public record SongDto(String name, float sizeNumber, String sizeUnit, String status) {

    @Builder
    public SongDto {
    }
}
