package com.bankstatement.common.dto;

import com.bankstatement.common.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreateAccountRequest {

    @NotBlank
    private String holderName;
    @NotBlank
    private String holderDocument;
    @NotNull
    private List<Currency> currencies;

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderDocument() { return holderDocument; }
    public void setHolderDocument(String holderDocument) { this.holderDocument = holderDocument; }
    public List<Currency> getCurrencies() { return currencies; }
    public void setCurrencies(List<Currency> currencies) { this.currencies = currencies; }
}
