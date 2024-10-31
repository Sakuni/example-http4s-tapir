import cats.effect._
import cats.syntax.semigroupk._
import io.circe.generic.auto._
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.util.concurrent.atomic.AtomicReference

object Main extends IOApp {

  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)
  case class Greeting(message: String)

  val helloEndpoint: PublicEndpoint[String, Unit, Greeting, Any] = endpoint.get
    .in("hello" / path[String]("name"))
    .out(jsonBody[Greeting])

  val goodbyeEndpoint: PublicEndpoint[String, Unit, Greeting, Any] = endpoint.get
    .in("goodbye" / path[String]("name"))
    .out(jsonBody[Greeting])

  val booksListingEndpoint: PublicEndpoint[Unit, Unit, Vector[Book], Any] = endpoint.get
    .in("books")
    .in("list" / "all")
    .out(jsonBody[Vector[Book]])

  val addBookEndpoint: PublicEndpoint[Book, Unit, String, Any] = endpoint.post
    .in("books" / "add")
    .in(jsonBody[Book])
    .out(stringBody)

  val deleteBookEndpoint: PublicEndpoint[String, String, String, Any] = endpoint.delete
    .in("books" / "delete" / path[String]("title"))
    .errorOut(stringBody)
    .out(stringBody)

  val updateBookEndpoint: PublicEndpoint[(String, Book), Unit, String, Any] = endpoint.put
    .in("books" / "update" / path[String]("title")) // Use path parameter for the book title
    .in(jsonBody[Book])
    .out(stringBody)

  val books = new AtomicReference(
    Vector(
      Book("The Sorrows of Young Werther", 1774, Author("Johann Wolfgang von Goethe")),
      Book("Iliad", -8000, Author("Homer")),
      Book("Nad Niemnem", 1888, Author("Eliza Orzeszkowa")),
      Book("The Colour of Magic", 1983, Author("Terry Pratchett")),
      Book("The Art of Computer Programming", 1968, Author("Donald Knuth")),
      Book("Pharaoh", 1897, Author("Boleslaw Prus"))
    )
  )

  val helloRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
    helloEndpoint.serverLogic(name => IO.pure(Right(Greeting(s"Hello, $name")): Either[Unit, Greeting]))
  )

  val goodbyeRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
    goodbyeEndpoint.serverLogic(name => IO.pure(Right(Greeting(s"Goodbye, $name")): Either[Unit, Greeting]))
  )
  val booksListingRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
    booksListingEndpoint.serverLogicSuccess(_ => IO(books.get()))
  )

  val addBookRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      addBookEndpoint.serverLogicSuccess { newBook =>
        IO {
          books.updateAndGet(_ :+ newBook)
          s"Book '${newBook.title}' by ${newBook.author.name} added successfully!"
        }
      })

  val deleteBookRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      deleteBookEndpoint.serverLogic { title =>
        IO {
          val updatedBooks = books.get().filterNot(_.title.equalsIgnoreCase(title))
          if (updatedBooks.size == books.get().size) {
            Left(s"Book with title '$title' not found")
          } else {
            books.set(updatedBooks)
            Right(s"Book with title '$title' deleted successfully!")
          }
        }
      })

  val updateBookRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      updateBookEndpoint.serverLogicSuccess { case (title, updatedBook) =>
        IO {
          val updatedBooks = books.get().map {
            case book if book.title == title => updatedBook
            case book => book
          }
          books.set(updatedBooks)
          s"Book '${updatedBook.title}' updated successfully!"
        }
      })

  // generating and exposing the documentation in yml
  val swaggerUIRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](List(helloEndpoint, goodbyeEndpoint, booksListingEndpoint, addBookEndpoint, deleteBookEndpoint,updateBookEndpoint), "My API", "1.0.0")
    )
  val routes: HttpRoutes[IO] = helloRoutes <+> goodbyeRoutes <+> booksListingRoutes <+> addBookRoutes <+> deleteBookRoutes <+> updateBookRoutes <+>  swaggerUIRoutes

  override def run(args: List[String]): IO[ExitCode] = {
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("localhost").getOrElse(Host.fromString("0.0.0.0").get))
      .withPort(Port.fromInt(8080).get)
      .withHttpApp(Router("/" -> routes).orNotFound)
      .build
      .useForever
      .as(ExitCode.Success)
  }
}