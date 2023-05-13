package kk.convert;

import lombok.Builder;

import java.util.List;

public record StatusDto(boolean downloadOngoing, List<SongDto> songs) {

    @Builder
    public StatusDto {
    }
}
