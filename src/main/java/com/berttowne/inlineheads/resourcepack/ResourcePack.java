package com.berttowne.inlineheads.resourcepack;

/**
 * Represents a resource pack found on the internet.
 *
 * @param url URL to the resource pack host
 * @param hash SHA-1 hash of the resource pack
 */
public record ResourcePack(String url, String hash) { }