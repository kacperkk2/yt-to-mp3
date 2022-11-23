package kk.convert;

import lombok.Builder;

public record SongDto(String name, String status) {

    @Builder
    public SongDto {
    }
}
