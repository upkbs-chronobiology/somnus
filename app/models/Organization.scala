package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

case class Organization(id: Long, name: String)

object Organization {
  implicit val implicitWrites = new Writes[Organization] {
    def writes(organization: Organization): JsValue = {
      Json.obj("id" -> organization.id, "name" -> organization.name)
    }
  }

  val tupled = (this.apply _).tupled
}

case class OrganizationFormData(name: String)

object OrganizationForm {
  val form = Form(mapping("name" -> nonEmptyText)(OrganizationFormData.apply)(OrganizationFormData.unapply))
}

class OrganizationTable(tag: Tag) extends Table[Organization](tag, "organization") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")

  override def * =
    (id, name) <> (Organization.tupled, Organization.unapply)
}

@Singleton
class OrganizationRepository @Inject() (dbConfigProvider: DatabaseConfigProvider) {

  private def organizations = TableQuery[OrganizationTable]

  private def dbConfig = dbConfigProvider.get[MySQLProfile]

  def listAll(): Future[Seq[Organization]] = {
    dbConfig.db.run(organizations.result)
  }

  def get(id: Long): Future[Option[Organization]] = {
    dbConfig.db.run(organizations.filter(_.id === id).result.headOption)
  }

  def create(organization: Organization): Future[Organization] = {
    val query = (organizations returning organizations.map(_.id)) += organization
    dbConfig.db.run(query) map (id => organization.copy(id = id))
  }

  def update(organization: Organization): Future[Option[Organization]] = {
    dbConfig.db
      .run(organizations.filter(_.id === organization.id).update(organization))
      .map {
        case 1 => Some(organization)
        case 0 => None
        case _ => throw new IllegalStateException("Updated more than 1 organizations, despite using ID")
      }
  }

  def delete(organizationId: Long): Future[Int] = {
    dbConfig.db.run(organizations.filter(_.id === organizationId).delete)
  }
}
