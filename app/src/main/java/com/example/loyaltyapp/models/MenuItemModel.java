package com.example.loyaltyapp.models;

public class MenuItemModel {
    private String id;
    private String name;
    private Double priceMAD;
    private String category;
    private String imageUrl;
    private Boolean isAvailable;
    private Boolean isPopular;
    private Long popularityScore;

    public MenuItemModel() { }

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public Double getPriceMAD() { return priceMAD; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getIsAvailable() { return isAvailable; }
    public Boolean getIsPopular() { return isPopular; }
    public Long getPopularityScore() { return popularityScore; }

    public void setName(String name) { this.name = name; }
    public void setPriceMAD(Double priceMAD) { this.priceMAD = priceMAD; }
    public void setCategory(String category) { this.category = category; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }
    public void setIsPopular(Boolean isPopular) { this.isPopular = isPopular; }
    public void setPopularityScore(Long popularityScore) { this.popularityScore = popularityScore; }
}
