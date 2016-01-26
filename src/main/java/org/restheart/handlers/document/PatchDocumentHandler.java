/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2016 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.document;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.restheart.db.DocumentDAO;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PatchDocumentHandler extends PipedHttpHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of PatchDocumentHandler
     */
    public PatchDocumentHandler() {
        this(null, new DocumentDAO());
    }

    public PatchDocumentHandler(DocumentDAO documentDAO) {
        super(null);
        this.documentDAO = documentDAO;
    }
    
    public PatchDocumentHandler(PipedHttpHandler next) {
        super(next);
        this.documentDAO = new DocumentDAO();
    }
    
    public PatchDocumentHandler(PipedHttpHandler next, DocumentDAO documentDAO) {
        super(next);
        this.documentDAO = documentDAO;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        DBObject content = context.getContent();

        // cannot PATCH with no data
        if (content == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "data is empty");
            return;
        }

        // cannot PATCH an array
        if (content instanceof BasicDBList) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            return;
        }

        Object id = context.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", id);
        } else if (!content.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in json data cannot be different than id in URL");
            return;
        }

        OperationResult result = documentDAO.upsertDocument(
                context.getDBName(),
                context.getCollectionName(),
                context.getDocumentId(),
                content,
                context.getETag(),
                true,
                context.isETagCheckRequired());
        
        context.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            exchange.getResponseHeaders().put(Headers.ETAG, result.getEtag().toString());
        }
        
        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_CONFLICT,
                    "The document's ETag must be provided using the '" + Headers.IF_MATCH + "' header");
            return;
        }
        
        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(result.getHttpCode(), exchange, context);
        } else {
            exchange.setStatusCode(result.getHttpCode());
        }
        
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
        
        exchange.endExchange();
    }
}
