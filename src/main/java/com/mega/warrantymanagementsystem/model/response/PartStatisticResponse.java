package com.mega.warrantymanagementsystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PartStatisticResponse {

    private String partId;
    private String partName;
    private int totalQuantity;
    private int isUsed;


}
