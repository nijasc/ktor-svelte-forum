-- Posts can contain raw HTML, and the text parser reads "<b>word</b>" as tag, blank, tag, so the word would never be
-- indexed. Tags are replaced by spaces first. The queries use search_text() as well, so index and query always agree.
CREATE FUNCTION search_text(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$ SELECT regexp_replace($1, '<[^>]*>', ' ', 'g') $$;

-- 'simple' means no stemming and no stop words, which suits a forum written in more than one language.
CREATE INDEX threads_search_idx ON threads USING GIN (to_tsvector('simple', search_text(title)));
CREATE INDEX posts_search_idx ON posts USING GIN (to_tsvector('simple', search_text(body)));
