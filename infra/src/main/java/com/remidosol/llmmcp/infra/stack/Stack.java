package com.remidosol.llmmcp.infra.stack;

import com.remidosol.llmmcp.infra.InfraConfig;
import io.cdktn.cdktn.GcsBackend;
import io.cdktn.cdktn.TerraformElement;
import io.cdktn.cdktn.TerraformOutput;
import io.cdktn.cdktn.TerraformStack;
import io.cdktn.providers.google.provider.GoogleProvider;
import software.constructs.Construct;
import software.constructs.Node;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Base stack (port of the reference {@code Stack}): GCS remote state under {@code <repo>/<stack>},
 * the Google provider, and readable logical ids — Terraform addresses become the construct path
 * joined with underscores, duplicates removed, instead of the default path + hash suffix.
 */
public abstract class Stack extends TerraformStack {

    protected final InfraConfig config;
    protected final GoogleProvider google;

    protected Stack(Construct scope, String id, InfraConfig config) {
        super(scope, id);
        this.config = config;
        GcsBackend.Builder.create(this)
                .bucket(config.stateBucket())
                .prefix(config.repoName() + "/" + id)
                .build();
        this.google = GoogleProvider.Builder.create(this, "google")
                .project(config.projectId())
                .region(config.region())
                .build();
    }

    @Override
    protected String allocateLogicalId(Object element) {
        if (element instanceof TerraformOutput || !(element instanceof TerraformElement terraformElement)) {
            return super.allocateLogicalId(element);
        }
        List<String> path = Node.of(terraformElement).getPath().length() == 0
                ? List.of() : List.of(Node.of(terraformElement).getPath().split("/"));
        if (path.size() < 2) {
            return super.allocateLogicalId(element);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(path.subList(1, path.size())); // drop the stack name
        return String.join("_", unique);
    }

    protected TerraformOutput output(String name, Object value) {
        return TerraformOutput.Builder.create(this, name).value(value).build();
    }
}
