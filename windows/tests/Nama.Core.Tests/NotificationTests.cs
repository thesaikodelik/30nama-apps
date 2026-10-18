using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>The shape 30nama's own desktop app reads; of the server's own, only an empty answer has been seen.</summary>
public class NotificationTests
{
    [Fact]
    public void ReadsTheServersAnswerForAnAccountWithNone()
    {
        var page = new NotificationPage(Json.Parse("""{"notifications":[],"page":1,"pages":0,"total":0,"total_unread":0}"""));
        Assert.Empty(page.Items);
        Assert.Equal(0, page.Unread);
        Assert.Equal(1, page.Page);
    }

    [Fact]
    public void ReadsTheShapeTheOfficialAppReads()
    {
        var page = new NotificationPage(Json.Parse("""
            {"notifications":[
              {"id":12,"title":"قسمت تازه","content":"<p>اپیزود 4 <b>فصل 2</b><br>اضافه شد</p>","read":false,"notification_type":"post",
               "post":{"id":469773,"title":{"english":"Link Click 2021"}},"date":{"g":"2026-10-16 10:00:00","j":"۱۴۰۵-۰۷-۲۴"}},
              {"id":"13","title":"Offer","notification_type":"pricing","read":1,"date":"2026-10-15"},
              {"title":"no id"}],
             "total_unread":1,"page":1}
            """));
        Assert.Equal(2, page.Items.Count); // one without an id is no notification
        Assert.Equal(1, page.Unread);
        var first = page.Items[0];
        Assert.Equal("12", first.Id);
        Assert.Equal("اپیزود 4 فصل 2\nاضافه شد", first.Content); // the HTML it comes with is taken out
        Assert.Equal(AccountNotification.Kinds.Post, first.Kind);
        Assert.Equal(469773, first.Post?.Id);
        Assert.Equal("۱۴۰۵-۰۷-۲۴", first.Date.Jalali);
        Assert.True(page.Items[1].Read);
        Assert.Equal("2026-10-15", page.Items[1].Date.Gregorian);
        Assert.Equal(AccountNotification.Kinds.Pricing, page.Items[1].Kind);
    }
}
