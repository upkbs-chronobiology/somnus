package exceptions

class ItemNotFoundException(message: String) extends IllegalArgumentException(message) {}
