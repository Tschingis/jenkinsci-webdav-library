package org.jenkins.library.webdav;

import java.io.File;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

@Grab(group='com.github.lookfirst', module='sardine', version='5.10')
import com.github.sardine.*;
import com.github.sardine.impl.SardineException;
import org.apache.http.client.*;


class WebDavClient {
    /**
     * Jenkins FilePath representing the 'local' directory. All local file
     * patterns are resolved within this directory
     */
    protected FilePath local;

    /**
     * Full path to 'remote' directory, including the protocol and hostname.
     * All remote paths are resolved relative to this directory.
     */
    protected String remote;

    protected String user;
    protected String pass;

    protected Sardine conn;

    def steps;

    WebDavClient(steps, local, remote, user, pass) {
        this.local = local;
        this.remote = remote;
        this.user = user;
        this.pass = pass;
        this.conn = SardineFactory.begin()
        this.conn.setCredentials(user, pass)
        this.steps = steps
    }

    def mkdir(path) {
        try {
            conn.createDirectory(path2url(path))
        } catch (HttpResponseException hte) {
            /*
             * Apache's mod_dav (at least) respond with '301 Moved permamently'
             * when the directory already exist. Do notning then.
             */
            if (hte.getStatusCode() != 301) throw hte;
        }
    }

    def rm(path) {
        try {
            conn.delete(path2url(path))
        } catch (HttpResponseException hte) {
            /*
             * Apache's mod_dav (at least) requires directories to be
             * referred with trailing slash and signals this with
             * '301 Moved permamently'. In that case, retry with
             * trailing slash.
             */
            if (hte.getStatusCode() == 301) {
                rm(path + DavResource.SEPARATOR)
            } else {
                throw hte
            }
        }
    }

    def put(path, pattern) {
        for (file in this.local.list(pattern)) {
            def dest = path2url(path + DavResource.SEPARATOR + file.getName());
            def temp = File.createTempFile("put", null);
            try {
                file.copyTo(new FilePath(temp));
                this.steps.echo "Uploading ${file.getName()}..."
                conn.put(dest, temp, (String)null);
                this.steps.echo "Uploaded ${file.getName()}"
            } finally {
                temp.delete()
            }
        }
    }


    def ls(path = null) {
        def resources = conn.list(path2url(path))
        resources.remove(0)
        return resources.collect { it.getName().toString() }
    }

    def isdir(path = null) {
        try {
            def resources = conn.list(path2url(path))
            return resources.size >= 0
        } catch (SardineException se) {
            return false;
        }
    }

    private def path2url(path) {
        if (path == null || path.equals('')) {
            return remote
        } else {
            return remote + DavResource.SEPARATOR + path
        }
    }
}