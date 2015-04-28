package se.kth.bbc.study;

import java.util.Date;
import java.util.List;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import se.kth.bbc.security.ua.model.User;
import se.kth.kthfsdashboard.user.AbstractFacade;

/**
 *
 * @author roshan
 */
@Stateless
public class StudyFacade extends AbstractFacade<Study> {

  @PersistenceContext(unitName = "kthfsPU")
  private EntityManager em;

  @Override
  protected EntityManager getEntityManager() {
    return em;
  }

  public StudyFacade() {
    super(Study.class);
  }

  @Override
  public List<Study> findAll() {
    TypedQuery<Study> query = em.createNamedQuery("Study.findAll",
            Study.class);
    return query.getResultList();
  }

  /**
   * Find all the studies for which the given user is owner. This implies that
   * this user created all the returned studies.
   * <p>
   * @param user The user for whom studies are sought.
   * @return List of all the studies that were created by this user.
   */
  public List<Study> findByUser(User user) {
    TypedQuery<Study> query = em.createNamedQuery(
            "Study.findByOwner", Study.class).setParameter(
                    "owner", user);
    return query.getResultList();
  }

  /**
   * Find all the studies for which the user with given email is owner. This
   * implies that this user created all the returned studies.
   * <p>
   * @param email The email of the user for whom studies are sought.
   * @return List of all the studies that were created by this user.
   * @deprecated use findByUser(User user) instead.
   */
  public List<Study> findByUser(String email) {
    TypedQuery<User> query = em.createNamedQuery(
            "User.findByEmail", User.class).setParameter(
                    "email", email);
    User user = query.getSingleResult();
    return findByUser(user);
  }

  /**
   * Get the study with the given name created by the given User.
   * <p>
   * @param studyname The name of the study.
   * @param user The owner of the study.
   * @return The study with given name created by given user, or null if such
   * does not exist.
   */
  public Study findByNameAndOwner(String studyname, User user) {
    TypedQuery<Study> query = em.createNamedQuery("Study.findByOwnerAndName",
            Study.class).setParameter("name", studyname).setParameter("owner",
                    user);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * Get the study with the given name created by the User with given email.
   * <p>
   * @param studyname The name of the study.
   * @param email The email of the owner of the study.
   * @return The study with given name created by given user, or null if such
   * does not exist.
   * @deprecated use findByNameAndOwner(String studyname, User user) instead.
   */
  public Study findByNameAndOwnerEmail(String studyname, String email) {
    TypedQuery<User> query = em.createNamedQuery("User.findByEmail",
            User.class).setParameter("email", email);
    User user = query.getSingleResult();
    return findByNameAndOwner(studyname, user);
  }

  /**
   * Count the number of studies for which the given user is owner.
   * <p>
   * @param owner
   * @return
   */
  public int countOwnedStudies(User owner) {
    TypedQuery<Long> query = em.createNamedQuery("Study.countStudyByOwner",
            Long.class);
    query.setParameter("owner", owner);
    return query.getSingleResult().intValue();
  }

  /**
   * Count the number of studies for which the owner has the given email.
   * <p>
   * @param email
   * @return The number of studies.
   * @deprecated Use countOwnedStudies(User owner) instead.
   */
  public int countOwnedStudies(String email) {
    TypedQuery<User> query = em.createNamedQuery("User.findByEmail", User.class);
    query.setParameter("email", email);
    //TODO: may throw an exception
    User user = query.getSingleResult();
    return countOwnedStudies(user);
  }

  /**
   * Find all the studies owned by the given user.
   * <p>
   * @param user
   * @return
   */
  public List<Study> findOwnedStudies(User user) {
    TypedQuery<Study> query = em.createNamedQuery("Study.findByOwner",
            Study.class);
    query.setParameter("owner", user);
    return query.getResultList();
  }

  /**
   * Get the owner of the given study.
   * <p>
   * @param study The study for which to get the current owner.
   * @return The primary key of the owner of the study.
   * @deprecated Use study.getOwner().getEmail(); instead.
   */
  public String findOwner(Study study) {
    return study.getOwner().getEmail();
  }

  /**
   * Find all the studies the given user is a member of.
   * <p>
   * @param user
   * @return
   */
  public List<Study> findAllMemberStudies(User user) {
    TypedQuery<Study> query = em.createNamedQuery("StudyTeam.findAllMemberStudiesForUser",
            Study.class);
    query.setParameter("user", user);
    return query.getResultList();
  }

  /**
   * Find all studies created (and owned) by this user.
   * <p>
   * @param user
   * @return
   */
  public List<Study> findAllPersonalStudies(User user) {
    TypedQuery<Study> query = em.createNamedQuery("Study.findByOwner",Study.class);
    query.setParameter("owner", user);
    return query.getResultList();
  }

  /**
   * Get all the studies this user has joined, but not created.
   * <p>
   * @param user
   * @return
   */
  public List<Study> findAllJoinedStudies(User user) {
    TypedQuery<Study> query = em.createNamedQuery("StudyTeam.findAllJoinedStudiesForUser",
            Study.class);
    query.setParameter("user", user);
    return query.getResultList();
  }

  public void persistStudy(Study study) {
    em.persist(study);
  }
  
  /**
   * Mark the study <i>study</i> as deleted.
   * @param study 
   */
  public void removeStudy(Study study){
    study.setDeleted(Boolean.TRUE);
    em.merge(study);
  }

  /**
   * Check if a study with this name already exists.
   * @param name
   * @return 
   */
  public boolean studyExists(String name) {
    TypedQuery<Study> query = em.createNamedQuery("Study.findByName",Study.class);
    query.setParameter("name", name);
    return !query.getResultList().isEmpty();
  }

  public void archiveStudy(String studyname) {
    Study study = findByName(studyname);
    if (study != null) {
      study.setArchived(true);
    }
    em.merge(study);
  }

  public void unarchiveStudy(String studyname) {
        Study study = findByName(studyname);
    if (study != null) {
      study.setArchived(false);
    }
    em.merge(study);
  }

  public boolean updateRetentionPeriod(String name, Date date) {
        Study study = findByName(name);
    if (study != null) {
      study.setRetentionPeriod(date);
      em.merge(study);
      return true;
    }
    return false;
  }

  public Date getRetentionPeriod(String name) {
    Study study = findByName(name);
    if (study != null) {
      return study.getRetentionPeriod();
    }
    return null;
  }
  
  private Study findByName(String name){
    TypedQuery<Study> query = em.createNamedQuery("Study.findByName",Study.class);
    query.setParameter("name", name);
    try{
      return query.getSingleResult();
    }catch(NoResultException e){
      return null;
    }
  }
}
