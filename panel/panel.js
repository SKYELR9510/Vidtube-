// Pide confirmación antes de borrar un enlace.
document.querySelectorAll('form.confirm-delete').forEach(function (f) {
  f.addEventListener('submit', function (e) {
    if (!window.confirm('¿Borrar este enlace?')) e.preventDefault();
  });
});
