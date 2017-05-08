package io.hops.hopsworks.api.jupyter;

import io.hops.hopsworks.api.filter.NoCacheResponse;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import io.hops.hopsworks.api.filter.AllowedRoles;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsers;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsersFacade;
import io.hops.hopsworks.common.dao.jupyter.JupyterProject;
import io.hops.hopsworks.common.dao.jupyter.config.JupyterConfigFactory;
import io.hops.hopsworks.common.dao.jupyter.config.JupyterDTO;
import io.hops.hopsworks.common.dao.jupyter.config.JupyterFacade;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.dao.user.security.ua.UserManager;
import io.hops.hopsworks.common.exception.AppException;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.GenericEntity;

@RequestScoped
@TransactionAttribute(TransactionAttributeType.NEVER)
public class JupyterService {

  private final static Logger LOGGER = Logger.getLogger(JupyterService.class.
          getName());

  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private UserManager userManager;
  @EJB
  private UserFacade userFacade;
  @EJB
  private JupyterConfigFactory jupyterConfigFactory;
  @EJB
  private JupyterFacade jupyterFacade;
  @EJB
  private HdfsUsersController hdfsUsersController;
  @EJB
  private HdfsUsersFacade hdfsUsersFacade;

  private Integer projectId;
  private Project project;

  public JupyterService() {
  }

  public void setProjectId(Integer projectId) {
    this.projectId = projectId;
    this.project = this.projectFacade.find(projectId);
  }

  public Integer getProjectId() {
    return projectId;
  }

  /**
   * Launches a Jupyter notebook server for this project-specific user
   *
   * @param sc
   * @param req
   * @return
   * @throws AppException
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER})
  public Response getAllNotebookServersInProject(
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {

    if (projectId == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "Incomplete request!");
    }

    Collection<JupyterProject> servers = project.getJupyterProjectCollection();

    if (servers == null) {
      throw new AppException(
              Response.Status.NOT_FOUND.getStatusCode(),
              "Could not find any Jupyter notebook servers for this project.");
    }

    List<JupyterProject> listServers = new ArrayList<>();
    listServers.addAll(servers);

    GenericEntity<List<JupyterProject>> notebookServers
            = new GenericEntity<List<JupyterProject>>(listServers) {};
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            notebookServers).build();
  }

  @GET
  @Path("/running")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response isMyNotebookServerRunning(@Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {

    if (projectId == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "Incomplete request!");
    }
    JupyterProject jp = jupyterFacade.findByUser(getHdfsUser(sc));
    if (jp == null) {
      throw new AppException(
              Response.Status.NOT_FOUND.getStatusCode(),
              "Could not find any Jupyter notebook server for this project.");
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            jp).build();
  }

  @POST
  @Path("/start")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response startNotebookServer(JupyterDTO jupyterConfig,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {

    if (projectId == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "Incomplete request!");
    }
    String hdfsUser = getHdfsUser(sc);
    if (hdfsUser == null) {
      throw new AppException(
              Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
              "Could not find your username. Report a bug.");
    }
    JupyterProject jp = jupyterFacade.findByUser(hdfsUser);
    if (jp == null) {
      HdfsUsers user = hdfsUsersFacade.findByName(hdfsUser);

      JupyterDTO dto;
      try {
        dto = jupyterConfigFactory.startServer(project, hdfsUser,
                jupyterConfig.getDriverCores(), jupyterConfig.getDriverMemory(),
                jupyterConfig.getNumExecutors(),
                jupyterConfig.getExecutorCores(), jupyterConfig.
                getExecutorMemory(), jupyterConfig.getGpus(),
                jupyterConfig.getArchives(), jupyterConfig.getJars(),
                jupyterConfig.getFiles(), jupyterConfig.getPyFiles());
      } catch (InterruptedException | IOException ex) {
        Logger.getLogger(JupyterService.class.getName()).log(Level.SEVERE, null,
                ex);
        throw new AppException(
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Problem starting a Jupyter notebook server.");
      }

      if (dto == null) {
        throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                "Incomplete request!");
      }

      jp = jupyterFacade.saveServer(project, dto.getPort(), user.getId(), dto.
              getToken(), dto.getPid(), dto.getDriverCores(), dto.
              getDriverMemory(), dto.getNumExecutors(), dto.getExecutorCores(),
              dto.getExecutorMemory(), dto.getGpus(), dto.getArchives(), dto.
              getJars(), dto.getFiles(), dto.getPyFiles());

      if (jp == null) {
        throw new AppException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Could not save Jupyter Settings.");
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            jp).build();
  }

  @DELETE
  @Path("/stop")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response stopNotebookServer(@Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    if (projectId == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "Incomplete request!");
    }

    String hdfsUser = getHdfsUser(sc);
    if (!jupyterConfigFactory.stopServer(hdfsUser)) {
      try {
        // The server may have been restarted and the caches are empty.
        // We need to stop the jupyter notebook server with the PID
        // If we can't stop the server, delete the Entity bean anyway
        JupyterProject jp = jupyterFacade.findByUser(hdfsUser);
        if (jp == null) {
          throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                  "No Jupyter Notebook server to stop");
        }
        Long pid = jp.getPid();
        ProcessBuilder ps = new ProcessBuilder("kill", pid.toString());
        Process pr = ps.start();
        pr.waitFor();
      } catch (IOException ex) {
        Logger.getLogger(JupyterService.class.getName()).log(Level.SEVERE, null,
                ex);
      } catch (InterruptedException ex) {
        Logger.getLogger(JupyterService.class.getName()).log(Level.SEVERE, null,
                ex);
      }
    }
    jupyterFacade.removeNotebookServer(hdfsUser);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
  }

  private String getHdfsUser(SecurityContext sc) throws AppException {
    if (projectId == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "Incomplete request!");
    }
    String loggedinemail = sc.getUserPrincipal().getName();
    Users user = userFacade.findByEmail(loggedinemail);
    if (user == null) {
      throw new AppException(Response.Status.UNAUTHORIZED.getStatusCode(),
              "You are not authorized for this invocation.");
    }
    String hdfsUsername = hdfsUsersController.getHdfsUserName(project, user);

    return hdfsUsername;
  }

}
