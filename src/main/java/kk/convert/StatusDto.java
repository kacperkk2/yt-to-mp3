package kk.convert;

import lombok.Builder;

import java.util.List;

public record StatusDto(boolean downloadOngoing, double totalSizeNumber,
                        String totalSizeUnit, List<SongDto> songs) {

    @Builder
    public StatusDto {
    }
}
