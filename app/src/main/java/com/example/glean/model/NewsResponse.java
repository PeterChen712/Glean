package com.example.glean.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class NewsResponse {
    @SerializedName("status")
    private String status;
    
    @SerializedName("totalResults")
    private int totalResults;
    
    @SerializedName("articles")
    private List<Article> articles;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalResults() { return totalResults; }
    public void setTotalResults(int totalResults) { this.totalResults = totalResults; }

    public List<Article> getArticles() { return articles; }
    public void setArticles(List<Article> articles) { this.articles = articles; }

    public static class Article {
        @SerializedName("source")
        private Source source;
        
        @SerializedName("author")
        private String author;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("description")
        private String description;
        
        @SerializedName("url")
        private String url;
        
        @SerializedName("urlToImage")
        private String urlToImage;
        
        @SerializedName("publishedAt")
        private String publishedAt;
        
        @SerializedName("content")
        private String content;

        // Getters and Setters
        public Source getSource() { return source; }
        public void setSource(Source source) { this.source = source; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getUrlToImage() { return urlToImage; }
        public void setUrlToImage(String urlToImage) { this.urlToImage = urlToImage; }

        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class Source {
        @SerializedName("id")
        private String id;
        
        @SerializedName("name")
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}