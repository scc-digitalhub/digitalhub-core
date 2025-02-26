package it.smartcommunitylabdhub.core.components.lucene;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class QueryMapper {
	private BooleanQuery completeQuery;
	private Query highlightQuery;
}
