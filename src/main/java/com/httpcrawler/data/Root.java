package com.httpcrawler.data;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import java.util.Objects;

@Immutable
@ParametersAreNonnullByDefault
public class Root {

    public final String urlString;
    public final int depth;
    public final int rand;

    public Root(String urlString, int depth, int rand) {
        this.urlString = urlString;
        this.depth = depth;
        this.rand = rand;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        Root other = (Root) obj;

        return this.depth == other.depth && this.rand == other.rand && Objects.equals(this.urlString, other.urlString);
    }

    @Override
    public int hashCode() {
        int result = (depth ^ (depth >>> 2));
        result = 31 * result + (rand ^ (rand >>> 2));
        result = 31 * result + urlString.hashCode();
        return result;
    }
}
